-- Durable commerce event feed polled by warehouse-service and payment-service
-- (GET /internal/v1/events); the backend no longer calls payment-service.
--
-- domain_events: one row per commerce event, written in the transaction that
-- changed the order. type is the wire name (order.created, card-refund.requested,
-- ...), payload the event's data as JSON with sorted keys. Consumers page by
-- sequence; rows are served once recorded_at is older than events.settle-delay
-- and deleted after events.retention (index on recorded_at).
--
-- card_refund_requests: every card refund requested from payment-service, kept
-- after its feed event expires so later refunds are capped at what is still
-- refundable on the captured payment. Rows go with their order.
--
-- payment_refund_outbox (V11) is replaced: refunds it still had to deliver
-- (PENDING) become card-refund.requested events, refunds that count against the
-- captured amount (PENDING and SENT) move to card_refund_requests, and the table
-- is dropped.
CREATE TABLE IF NOT EXISTS domain_events (
    sequence bigserial PRIMARY KEY,
    event_id uuid NOT NULL,
    type varchar(64) NOT NULL,
    order_id bigint,
    username varchar(255),
    occurred_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL,
    payload text NOT NULL,
    CONSTRAINT uk_domain_events_event UNIQUE (event_id)
);
CREATE INDEX IF NOT EXISTS idx_domain_events_recorded_at ON domain_events(recorded_at);

DO $migration$
BEGIN
    IF to_regclass('orders') IS NULL THEN
        RETURN;
    END IF;

    CREATE TABLE card_refund_requests (
        request_id uuid PRIMARY KEY,
        order_id bigint NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
        payment_id uuid NOT NULL,
        amount numeric(10,2) NOT NULL CHECK (amount > 0),
        reason varchar(255),
        created_at timestamptz NOT NULL
    );
    CREATE INDEX idx_card_refund_requests_order ON card_refund_requests(order_id);

    IF to_regclass('payment_refund_outbox') IS NULL THEN
        RETURN;
    END IF;

    INSERT INTO card_refund_requests (request_id, order_id, payment_id, amount, reason, created_at)
    SELECT request_id, order_id, payment_id, amount, reason, created_at
    FROM payment_refund_outbox
    WHERE status IN ('PENDING', 'SENT');

    -- Same data and key order as DomainEventPayloads: amount, orderId, reason, requestId.
    -- The event id is derived from the requestId so the migration is deterministic.
    INSERT INTO domain_events (event_id, type, order_id, username, occurred_at, recorded_at, payload)
    SELECT md5('card-refund.requested:' || o.request_id::text)::uuid,
           'card-refund.requested',
           o.order_id,
           ord.username,
           date_trunc('milliseconds', o.created_at),
           now(),
           '{"amount":' || o.amount::text
               || ',"orderId":' || o.order_id::text
               || ',"reason":' || COALESCE(to_json(o.reason)::text, 'null')
               || ',"requestId":"' || o.request_id::text || '"}'
    FROM payment_refund_outbox o
    JOIN orders ord ON ord.id = o.order_id
    WHERE o.status = 'PENDING'
    ORDER BY o.created_at, o.id;

    DROP TABLE payment_refund_outbox;
END
$migration$;
