/**
 * HTTP exception advice for controller-layer responses.
 *
 * <p>{@link com.awesome.testing.controller.exception.GlobalExceptionHandlerController}
 * is intentionally unscoped and should contain only cross-cutting API behavior
 * that applies consistently across controllers, such as validation, authentication,
 * authorization, and shared {@link com.awesome.testing.controller.exception.CustomException}
 * responses.
 *
 * <p>Domain-specific advice classes must be scoped with
 * {@code @RestControllerAdvice(assignableTypes = ...)} so that one controller's
 * exception mapping does not accidentally change another controller's contract.
 */
package com.awesome.testing.controller.exception;
