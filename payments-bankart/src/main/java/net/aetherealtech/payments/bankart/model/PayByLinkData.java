package net.aetherealtech.payments.bankart.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * What came back about an issued pay-by-link.
 *
 * <p>The two published sources describe two different objects under this one name, and this is the
 * prose documentation's version. The OpenAPI schema declares {@code payByLinkData} as two booleans,
 * {@code payByLink} and {@code sendViaEmail} — an echo of the request that tells a caller nothing it
 * did not already know. The prose documentation's own field table and JSON example instead publish
 * {@code expiresAt} and {@code cancelUrl}, which are facts only the gateway holds. The schema is
 * taken to be stale; both fields are read leniently, so a connector sending the schema's shape
 * simply leaves these null rather than failing to parse.
 *
 * @param expiresAt when the link stops being payable, e.g. {@code 2023-08-21 14:13:02 UTC} — note
 *                  the space-separated, {@code UTC}-suffixed spelling, which is not the
 *                  {@code DateTimeZone} format the rest of the API uses
 * @param cancelUrl the endpoint that revokes the link. Its route,
 *                  {@code /api/v3/payByLink/{id}/cancel}, appears in no path definition in either
 *                  source — only inside example values of this field — so its verb and body are
 *                  unknown and no method here calls it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PayByLinkData(String expiresAt, String cancelUrl) {
}
