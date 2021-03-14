package jokrey.mockchain.visualization

import jokrey.mockchain.Mockchain
import jokrey.mockchain.Nockchain
import jokrey.mockchain.application.examples.calculator.SingleStringCalculator
import jokrey.mockchain.visualization.util.IntegersOnlyDocument
import jokrey.mockchain.visualization.util.LabeledInputField
import jokrey.utilities.animation.implementations.swing.display.AnimationJPanel
import jokrey.utilities.animation.implementations.swing.display.Swing_FullScreenStarter
import jokrey.utilities.animation.implementations.swing.pipeline.AnimationDrawerSwing
import jokrey.utilities.animation.pipeline.AnimationPipeline
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Toolkit
import java.util.concurrent.CancellationException
import javax.swing.*

fun main() {
    val app = SingleStringCalculator()
    VisualizationFrame(Mockchain(app))
}

/**
 * Always shows the current state of the chain and allows adding custom transactions.
 *
 * Swappable: App, Consensus(different control schemes for each), NetworkCapabilities (with turn off)
 * Maybe: On start click through all decisions: DropDown for app, dropdown for consensus, checkbox for network
 *
 * @author jokrey
 */
class VisualizationFrame(val instance: Mockchain, allowSwitchApp: Boolean = true) {
    val engine: TxVisualizationEngine = TxVisualizationEngine(
        instance, (instance.app as VisualizableApp), (instance.app as VisualizableApp)
    )
    val pipe: AnimationPipeline

    init {
        val frame = JFrame(instance.app::class.java.simpleName)

        var ap: AnimationJPanel? = null
        pipe = object : AnimationPipeline(AnimationDrawerSwing()) {
        }

        val chainInformationDisplay = JLabel(getChainInfoText())
        val appInformationDisplay = JLabel(getAppInfoText())

        instance.chain.store.addCommittedChangeListener {
            engine.recalculateTransactionDisplay()
            if (pipe.userDrawBoundsMidOverride == null)
                pipe.resetDrawBounds(engine)

            chainInformationDisplay.text = getChainInfoText()
            appInformationDisplay.text = getAppInfoText()
        }
        instance.memPool.addChangeListener {
            //todo - only update mem pool part of display
            engine.recalculateTransactionDisplay()
            if (pipe.userDrawBoundsMidOverride == null)
                pipe.resetDrawBounds(engine)

            chainInformationDisplay.text = getChainInfoText()
        }


        val tickControlPanel = getTickControlPanel(this)
        val addTxJB = JButton("add custom tx")
        addTxJB.addActionListener {
            val input = JOptionPane.showInputDialog(frame, "Please enter new tx in string form -\nshould roughly correspond to short tx identifier\nNote: does not work with all applications", "Enter new tx in short string representation", JOptionPane.QUESTION_MESSAGE)

            try {
                if (input == null)
                    throw NullPointerException()
                else
                    instance.commitToMemPool((instance.app as VisualizableApp).createTxFrom(input))
            } catch (e: Exception) {
                e.printStackTrace()
                JOptionPane.showMessageDialog(frame, "Error - could not generate transaction\n${e.message}")
            }
        }
        val applicationChooserJB = JButton("switch App")
        applicationChooserJB.addActionListener {
            try {
                buildNewInstanceConfiguration(instance.app as VisualizableApp, instance.consensus.getCreator(), frame)
                frame.dispose()
            } catch (e: CancellationException) {
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(frame, "Error, could not create app.\nCheck input parameters and try again.\nError:\n${e.message}", "Could not create app", JOptionPane.ERROR_MESSAGE)
            }
        }
        val resetAppApplyReplayJB = JButton("reset from chain")
        resetAppApplyReplayJB.addActionListener {
            instance.app = instance.chain.applyReplayTo(instance.app.newEqualInstance())

            engine.recalculateTransactionDisplay()
            if (pipe.userDrawBoundsMidOverride == null)
                pipe.resetDrawBounds(engine)
        }


        ap = AnimationJPanel(engine, pipe)
        frame.add(ap, BorderLayout.CENTER)

        val footerPanel = JPanel()
        footerPanel.layout = BoxLayout(footerPanel, BoxLayout.X_AXIS)
        val consensusControlPanel = getAppropriateConsensusControlPanel(this, instance.consensus)
        if (consensusControlPanel != null) {
            footerPanel.add(consensusControlPanel)
            consensusControlPanel.border = BorderFactory.createMatteBorder(0, 0, 0, 3, Color.black)
        }
        footerPanel.add(tickControlPanel)
        footerPanel.add(addTxJB)
        if(allowSwitchApp)
            footerPanel.add(applicationChooserJB)
        footerPanel.add(resetAppApplyReplayJB)
        frame.add(footerPanel, BorderLayout.SOUTH)

        val headerPanel = JPanel(BorderLayout())
        val networkControlPanel = getAppropriateNetworkControlPanel(this, if(instance is Nockchain) instance.node else null)
        if(networkControlPanel != null)
            headerPanel.add(networkControlPanel, BorderLayout.CENTER)
        val headerMinPanel = JPanel()
        headerMinPanel.layout = BoxLayout(headerMinPanel, BoxLayout.X_AXIS)
        headerMinPanel.add(chainInformationDisplay)
        headerMinPanel.add(appInformationDisplay)
        headerPanel.add(headerMinPanel, BorderLayout.SOUTH)
        frame.add(headerPanel, BorderLayout.NORTH)

        val screenSize = Toolkit.getDefaultToolkit().screenSize
        frame.size = Dimension((screenSize.width*0.75).toInt(), (screenSize.height*0.75).toInt())
        Swing_FullScreenStarter.centerOnMouseScreen(frame)

        frame.isVisible = true
    }

    private fun getAppInfoText() = "| app:{"+(instance.app as VisualizableApp).shortStateDescriptor()+"} |"
    private fun getChainInfoText() = "| chain:{blocks(${instance.chain.blockCount()}), latestBlockHash(${instance.chain.getLatestHash()}), persistedTxNum(${instance.chain.persistedTxCount()})} |"

    fun recalculateDisplay() {
        engine.recalculateTransactionDisplay()
        pipe.resetDrawBounds(engine)
    }
}

fun <T> Array<T>.allEqual(): Boolean = distinct().size == 1

private fun getTickControlPanel(frame: VisualizationFrame) :JPanel {
    val tickJB = JButton("Tick")
    val epochNumberInputField = LabeledInputField("ticks per epoch", 5)
    val epochJB = JButton("Epoch")

    tickJB.addActionListener {
        frame.engine.calculateTick()
    }
    epochNumberInputField.setDocument(IntegersOnlyDocument())
    epochNumberInputField.text = "50"
    epochJB.addActionListener {
        for(i in 0 until epochNumberInputField.text.toInt())
            frame.engine.calculateTick()
    }

    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
    panel.add(tickJB)
    panel.add(epochNumberInputField)
    panel.add(epochJB)
    panel.border = BorderFactory.createMatteBorder(0, 1, 0, 1, Color.black)
    return panel
}