package jokrey.mockchain.visualization

import jokrey.mockchain.application.Application
import jokrey.mockchain.application.TransactionGenerator
import jokrey.mockchain.storage_classes.Transaction

/**
 * More demanding interface, to turns a simple blockchain application into one visualizable by the visualization engine.
 */
interface VisualizableApp : Application, ApplicationDisplay, TransactionGenerator, InteractivelyCreatableClass {
    /**
     * Creates a new transaction from the given input.
     * If the formatting of the string is roughly like return of {@link ApplicationDisplay.shortDescriptor} or {@link ApplicationDisplay.longDescriptor} it should yield a correct result.
     * Otherwise the method can throw an exception.
     */
    fun createTxFrom(input: String): Transaction

    override fun getEqualFreshCreator() : ()->VisualizableApp
    override fun createNewInstance(vararg params: String) : VisualizableApp
    override fun newEqualInstance(): VisualizableApp = getEqualFreshCreator()()
}