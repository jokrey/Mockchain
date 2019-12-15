package jokrey.mockchain.storage_classes

/**
 * Used to convey the reason for a transaction rejection to the application.
 *
 * Notes which part of the verification process permanently rejected the transaction and why
 */
sealed class RejectionReason(val description: String) {
    class APP_VERIFY(description: String) : RejectionReason(description)
    class SQUASH_VERIFY(description: String) : RejectionReason(description)
    class PRE_MEM_POOL(description: String) : RejectionReason(description)

    override fun toString(): String {
        return javaClass.simpleName + " - " + description
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return other is RejectionReason && description == other.description
    }
    override fun hashCode() = description.hashCode()
}