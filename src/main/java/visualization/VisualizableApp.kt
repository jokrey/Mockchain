package visualization

import application.Application
import application.TransactionGenerator
import storage_classes.Transaction

/**
 * More demanding interface, to turns a simple blockchain application into one visualizable by the visualization engine.
 */
interface VisualizableApp : Application, ApplicationDisplay, TransactionGenerator {
    /**
     * Returns a function that creates a new instance of the current application - same initial parameter, but a fresh, empty state
     */
    fun getEqualFreshCreator() : ()->VisualizableApp

    /**
     * Returns the parameter names of the application in the order expected by the {@link createNewInstance} method
     */
    fun getCreatorParamNames(): Array<String>
    /**
     * Returns the parameters that were used to create a fresh instance of the current application
     * If the contents of the array returned by this function are passed into {@link createNewInstance} it should have the same effect as calling {@link getEqualFreshCreator} directly.
     */
    fun getCurrentParamContentForEqualCreation() : Array<String>
    /**
     * Creates a new instance of the current application with potential different initial parameters and a fresh, empty state.
     * The method can expect the number of parameters returned by both {@link getCreatorParamNames} and {@link getCurrentParamContentForEqualCreation}.
     */
    fun createNewInstance(vararg params: String) : VisualizableApp

    /**
     * Creates a new transaction from the given input.
     * If the formatting of the string is roughly like return of {@link ApplicationDisplay.shortDescriptor} or {@link ApplicationDisplay.longDescriptor} it should yield a correct result.
     * Otherwise the method can throw an exception.
     */
    fun createTxFrom(input: String): Transaction
}