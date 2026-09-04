package net.aetherealtech.payments.bankart.model;

/**
 * What the gateway is willing to say about the instrument a transaction actually used.
 *
 * <p>Four variants, discriminated on the wire by {@code _TYPE}: {@code cardData}, {@code phoneData},
 * {@code ibanData} and {@code walletData}. There is no fifth — SEPA is {@code ibanData}, and there
 * is no un-suffixed "card".
 *
 * <p>Sealed rather than open because the set is the gateway's to change, not a consumer's to extend,
 * and because a {@code switch} over it should stop compiling on the day a fifth arrives instead of
 * silently falling through. A variant this library has not modelled reads as null; it never reads as
 * the wrong variant.
 */
public sealed interface ReturnData
        permits CardData, ReturnPhoneData, ReturnIbanData, ReturnWalletData {

    /** The {@code _TYPE} discriminator exactly as it arrived, e.g. {@code cardData}. */
    String type();
}
