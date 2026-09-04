package net.aetherealtech.payments.bankart.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Customer data attached to a transaction.
 *
 * <p>Every field is optional to the gateway, but supplying billing and shipping detail is what
 * feeds the 3-D Secure 2 frictionless flow — the docs' phrasing is to provide as much as you have.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Customer(
        String identification,
        String firstName,
        String lastName,
        String birthDate,
        String gender,
        String billingAddress1,
        String billingAddress2,
        String billingCity,
        String billingPostcode,
        String billingState,
        String billingCountry,
        String billingPhone,
        String shippingFirstName,
        String shippingLastName,
        String shippingCompany,
        String shippingAddress1,
        String shippingAddress2,
        String shippingCity,
        String shippingPostcode,
        String shippingState,
        String shippingCountry,
        String shippingPhone,
        String company,
        String email,
        Boolean emailVerified,
        String ipAddress,
        String nationalId,
        Map<String, String> extraData,
        PaymentData paymentData) {

    public static Builder builder() {
        return new Builder();
    }

    /** Twenty-nine optional fields is past the point where positional construction is readable. */
    public static final class Builder {
        private String identification;
        private String firstName;
        private String lastName;
        private String birthDate;
        private String gender;
        private String billingAddress1;
        private String billingAddress2;
        private String billingCity;
        private String billingPostcode;
        private String billingState;
        private String billingCountry;
        private String billingPhone;
        private String shippingFirstName;
        private String shippingLastName;
        private String shippingCompany;
        private String shippingAddress1;
        private String shippingAddress2;
        private String shippingCity;
        private String shippingPostcode;
        private String shippingState;
        private String shippingCountry;
        private String shippingPhone;
        private String company;
        private String email;
        private Boolean emailVerified;
        private String ipAddress;
        private String nationalId;
        private Map<String, String> extraData;
        private PaymentData paymentData;

        public Builder identification(String v) { this.identification = v; return this; }
        public Builder firstName(String v) { this.firstName = v; return this; }
        public Builder lastName(String v) { this.lastName = v; return this; }
        public Builder birthDate(String v) { this.birthDate = v; return this; }
        public Builder gender(String v) { this.gender = v; return this; }
        public Builder billingAddress1(String v) { this.billingAddress1 = v; return this; }
        public Builder billingAddress2(String v) { this.billingAddress2 = v; return this; }
        public Builder billingCity(String v) { this.billingCity = v; return this; }
        public Builder billingPostcode(String v) { this.billingPostcode = v; return this; }
        public Builder billingState(String v) { this.billingState = v; return this; }
        public Builder billingCountry(String v) { this.billingCountry = v; return this; }
        public Builder billingPhone(String v) { this.billingPhone = v; return this; }
        public Builder shippingFirstName(String v) { this.shippingFirstName = v; return this; }
        public Builder shippingLastName(String v) { this.shippingLastName = v; return this; }
        public Builder shippingCompany(String v) { this.shippingCompany = v; return this; }
        public Builder shippingAddress1(String v) { this.shippingAddress1 = v; return this; }
        public Builder shippingAddress2(String v) { this.shippingAddress2 = v; return this; }
        public Builder shippingCity(String v) { this.shippingCity = v; return this; }
        public Builder shippingPostcode(String v) { this.shippingPostcode = v; return this; }
        public Builder shippingState(String v) { this.shippingState = v; return this; }
        public Builder shippingCountry(String v) { this.shippingCountry = v; return this; }
        public Builder shippingPhone(String v) { this.shippingPhone = v; return this; }
        public Builder company(String v) { this.company = v; return this; }
        public Builder email(String v) { this.email = v; return this; }
        public Builder emailVerified(Boolean v) { this.emailVerified = v; return this; }
        public Builder ipAddress(String v) { this.ipAddress = v; return this; }
        public Builder nationalId(String v) { this.nationalId = v; return this; }
        public Builder extraData(Map<String, String> v) { this.extraData = v; return this; }
        public Builder paymentData(PaymentData v) { this.paymentData = v; return this; }

        public Customer build() {
            return new Customer(identification, firstName, lastName, birthDate, gender,
                    billingAddress1, billingAddress2, billingCity, billingPostcode, billingState,
                    billingCountry, billingPhone, shippingFirstName, shippingLastName, shippingCompany,
                    shippingAddress1, shippingAddress2, shippingCity, shippingPostcode, shippingState,
                    shippingCountry, shippingPhone, company, email, emailVerified, ipAddress,
                    nationalId, extraData, paymentData);
        }
    }
}
