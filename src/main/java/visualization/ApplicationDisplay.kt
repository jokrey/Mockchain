package visualization

import storage_classes.Transaction

/**
 * Further, optional requirement of a standard blockchain application to be able to better visualize the application in the visualization engine.
 */
interface ApplicationDisplay {
    /**
     * Should return a short, possibly cryptic description of the given transaction.
     * The transaction given can be expected to be a transaction verified by the applications verification algorithm.
     */
    fun shortDescriptor(tx:Transaction) : String
    /**
     * Should return a long, detailed description of the given transaction.
     * The transaction given can be expected to be a transaction verified by the applications verification algorithm.
     */
    fun longDescriptor(tx:Transaction) : String
    /**
     * Should return a short, possibly cryptic description of the current state. Does not have to include the entire state.
     */
    fun shortStateDescriptor(): String
    /**
     * Should return a long, detailed description of the current state. Should include the entire state
     * Does not have to work for very large application states.
     */
    fun exhaustiveStateDescriptor(): String
}