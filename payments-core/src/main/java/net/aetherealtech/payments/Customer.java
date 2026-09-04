package net.aetherealtech.payments;

/**
 * The buyer, in the few fields every provider modelled here understands.
 *
 * <p>Deliberately thin. Bankart's own customer object carries twenty-nine fields, most of them feeding
 * the 3-D Secure frictionless flow; AgentaOS carries six. Widening this record to the union would put
 * fields on the SPI that most adapters must silently drop, which reads at the call site exactly like a
 * field that works. A caller who needs Bankart's shipping address builds a Bankart request directly —
 * the adapter's own types stay reachable below the SPI, and that is the point of the layering.
 *
 * <p>Every field is optional to every provider modelled here; a null is a fact, not an omission to
 * apologise for.
 *
 * @param reference   the caller's own identifier for this buyer, echoed back where a provider supports it
 * @param email       the buyer's email address
 * @param name        the buyer's full name as one string, because the providers disagree on splitting it
 * @param countryCode ISO 3166-1 alpha-2, upper case
 * @param vatNumber   the buyer's VAT identification number, for a business buyer
 */
public record Customer(String reference, String email, String name, String countryCode, String vatNumber) {

    /** The common case: a buyer known by an email address alone. */
    public static Customer withEmail(final String email) {
        return new Customer(null, email, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String reference;
        private String email;
        private String name;
        private String countryCode;
        private String vatNumber;

        private Builder() {
        }

        public Builder reference(final String reference) {
            this.reference = reference;
            return this;
        }

        public Builder email(final String email) {
            this.email = email;
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder countryCode(final String countryCode) {
            this.countryCode = countryCode;
            return this;
        }

        public Builder vatNumber(final String vatNumber) {
            this.vatNumber = vatNumber;
            return this;
        }

        public Customer build() {
            return new Customer(reference, email, name, countryCode, vatNumber);
        }
    }
}
