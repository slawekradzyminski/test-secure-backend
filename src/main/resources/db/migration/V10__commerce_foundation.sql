-- Customer email templates added by the commerce foundation: payment results,
-- carrier tracking updates, loyalty points and gift cards. The constraint is
-- replaced by name; V7 created it as ck_email_event_type.
DO $migration$
BEGIN
    IF to_regclass('email_event') IS NULL THEN
        RETURN;
    END IF;

    ALTER TABLE email_event DROP CONSTRAINT IF EXISTS ck_email_event_type;
    ALTER TABLE email_event
        ADD CONSTRAINT ck_email_event_type CHECK (type IN (
            'GENERIC', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_CONFIRMED',
            'BACK_IN_STOCK', 'ORDER_SHIPPED', 'ORDER_DELIVERED', 'RETURN_UPDATED',
            'PAYMENT_RECEIVED', 'PAYMENT_FAILED', 'SHIPMENT_OUT_FOR_DELIVERY', 'SHIPMENT_DELIVERY_FAILED',
            'LOYALTY_POINTS_EARNED', 'GIFT_CARD_ISSUED'));
END
$migration$;
