package jokrey.mockchain.visualization

import jokrey.mockchain.visualization.util.LabeledInputField
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.util.concurrent.CancellationException
import javax.swing.BoxLayout
import javax.swing.JComboBox
import javax.swing.JOptionPane
import javax.swing.JPanel

/**
 *
 * @author jokrey
 */
interface InteractivelyCreatableClass {
    /**
     * Returns a function that creates a new instance of the current application - same initial parameter, but a fresh, empty state
     */
    fun getEqualFreshCreator() : ()->InteractivelyCreatableClass

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
    fun createNewInstance(vararg params: String) : InteractivelyCreatableClass
}


fun createChooser(options: Array<out InteractivelyCreatableClass>, current: InteractivelyCreatableClass? = null, frame: Component? = null): InteractivelyCreatableClass {
    val content = object : JPanel(BorderLayout()) {
        override fun getPreferredSize() = Dimension(600, 200)
    }

    val paramsPanel = JPanel()
    paramsPanel.layout = BoxLayout(paramsPanel, BoxLayout.X_AXIS)
    content.add(paramsPanel, BorderLayout.CENTER)

    val nameChooser = JComboBox(options.map { it::class.java.simpleName }.toTypedArray())
    nameChooser.addActionListener {
        val selectedApp = options[nameChooser.selectedIndex]

        val paramNames = selectedApp.getCreatorParamNames()

        paramsPanel.removeAll()
        paramsPanel.isVisible = false
        if(paramNames.isEmpty()) {
        } else {
            for(paramName in paramNames) {
                paramsPanel.add(LabeledInputField(paramName, 5))
            }
            paramsPanel.isVisible = true
        }
    }
    content.add(nameChooser, BorderLayout.NORTH)

    if(current != null) {
        nameChooser.selectedItem = current::class.java.simpleName
        for ((i, paramInput) in paramsPanel.components.withIndex())
            if (paramInput is LabeledInputField)
                paramInput.text = (current.getCurrentParamContentForEqualCreation()[i])
    } else {
        nameChooser.selectedIndex = 0
    }

    val result = JOptionPane.showConfirmDialog(frame, content, "", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE)

    if(result != 0) throw CancellationException()

    val selectedApp = options[nameChooser.selectedIndex]
    val params = ArrayList<String>()
    for(paramInput in paramsPanel.components)
        if(paramInput is LabeledInputField)
            params.add(paramInput.text)

    return selectedApp.createNewInstance(*params.toTypedArray())
}