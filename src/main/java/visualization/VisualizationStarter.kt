package visualization

import application.TransactionGenerator
import application.examples.calculator.MashedCalculator
import application.examples.calculator.MultiStringCalculator
import application.examples.calculator.SingleStringCalculator
import application.examples.currency.Currency
import application.examples.currency.CurrencyWithHistory
import application.examples.sensornet.SensorNetAnalyzer
import application.examples.supplychain.SupplyChain
import jokrey.utilities.animation.engine.AnimationEngine
import jokrey.utilities.animation.implementations.swing.display.AnimationJPanel
import jokrey.utilities.animation.implementations.swing.display.Swing_FullScreenStarter
import jokrey.utilities.animation.implementations.swing.pipeline.AnimationDrawerSwing
import jokrey.utilities.animation.pipeline.AnimationPipeline
import jokrey.utilities.animation.util.AEColor
import jokrey.utilities.animation.util.AERect
import storage_classes.Chain
import visualization.util.IntegersOnlyDocument
import visualization.util.LabeledInputField
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Toolkit
import java.util.concurrent.CancellationException
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener


val availableApps = arrayOf(
    SensorNetAnalyzer(),
    SingleStringCalculator(),
    MultiStringCalculator(1),
    MashedCalculator(1, maxDependencies = 1),
    Currency(),
    CurrencyWithHistory(),
    SupplyChain()
)

fun main() {
//    startVisualizationWith(SensorNetAnalyzer()) //works great, always
    startVisualizationWith(SingleStringCalculator()) //works great, always
//    startVisualizationWith(SingleStringCalculator {it.amount.toInt() % 10 != 4}) //works great, always - even with a weird verify
//    startVisualizationWith(MultiStringCalculator(40)) //works great, always
//    startVisualizationWith(MashedCalculator(20, maxDependencies = 10)) //works great, always
//    startVisualizationWith(Currency()) //works great, always
//    startVisualizationWith(SupplyChain(55, 5)) //works great, always
}


fun startApplicationChooser(frame: Component?, current: VisualizableApp, squashEveryNRounds: Int, consensusEveryNTicks: Int) {
    val content = object : JPanel(BorderLayout()) {
        override fun getPreferredSize() = Dimension(600, 200)
    }

    val paramsPanel = JPanel()
    paramsPanel.layout = BoxLayout(paramsPanel, BoxLayout.X_AXIS)
    content.add(paramsPanel, BorderLayout.CENTER)

    val nameChooser = JComboBox(availableApps.map { it::class.java.simpleName }.toTypedArray())
    nameChooser.addActionListener {
        val selectedApp = availableApps[nameChooser.selectedIndex]

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

        nameChooser.selectedItem = current::class.java.simpleName
        for((i, paramInput) in paramsPanel.components.withIndex())
            if(paramInput is LabeledInputField)
                paramInput.setText(current.getCurrentParamContentForEqualCreation()[i])

    val result = JOptionPane.showConfirmDialog(frame, content, "", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE)

    if(result != 0) throw CancellationException()

    val selectedApp = availableApps[nameChooser.selectedIndex]
    val params = ArrayList<String>()
    for(paramInput in paramsPanel.components)
        if(paramInput is LabeledInputField)
            params.add(paramInput.getText())

    val newApp = selectedApp.createNewInstance(*params.toTypedArray())
    startVisualizationWith(newApp, squashEveryNRounds, consensusEveryNTicks)
}


fun startVisualizationWith(app: VisualizableApp, squashEveryNRounds:Int = -1, consensusEveryNTicks:Int = 5) {
    val txGen:TransactionGenerator = app
    val appDisplay: ApplicationDisplay = app

    val chain = Chain(app, squashEveryNRounds = squashEveryNRounds)
    val engine = TxVisualizationEngine(chain, txGen, appDisplay, consensusEveryNTicks)
    var ap: AnimationJPanel? = null
    val pipe = object: AnimationPipeline(AnimationDrawerSwing()) {
        override fun drawForeground(drawBounds: AERect, engine: AnimationEngine?) {
            super.drawForeground(drawBounds, engine)

            drawer.drawString(AEColor.WHITE, appDisplay.shortStateDescriptor(), AERect(0.0,ap!!.height.toDouble()-75.0, ap!!.width.toDouble(), 75.0))
        }
    }

    val frame = JFrame(app::class.java.simpleName)

    ap = AnimationJPanel(engine, pipe)
    frame.add(ap, BorderLayout.CENTER)

    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    val screenSize = Toolkit.getDefaultToolkit().screenSize
    frame.size = Dimension((screenSize.width*0.75).toInt(), (screenSize.height*0.75).toInt())
    Swing_FullScreenStarter.centerOnMouseScreen(frame)

//tedious ui functionality:
    //ui declarations
    val footerPanel = JPanel()
    val squashJB = JButton("Squash")
    val performConsensusJB = JButton("Perform Consensus")
    val tickJB = JButton("Tick")
    val epochNumberInputField = LabeledInputField("ticks per epoch", 5)
    val epochJB = JButton("Epoch")
    val consensusEveryNTicksInputField = LabeledInputField("consensus every", 5)
    consensusEveryNTicksInputField.toolTipText = "consensus every X ticks(negative would be degree of randomness)"
    val squashEveryNConsensusRoundsInputField = LabeledInputField("squash every", 5)
    consensusEveryNTicksInputField.toolTipText = "squash every X consensus rounds (negative implies no-auto-squash)"
    val addTxJB = JButton("add custom tx")
    val applicationChooser = JButton("switch App")

    //ui functionality
    squashJB.addActionListener {
        chain.performConsensusRound(false) //required, otherwise the later performConsensusRound with squash will not have the same persistent state

        val priorStorageRequirements = chain.calculateStorageRequirementsInBytes()

        val stateBeforeString = appDisplay.exhaustiveStateDescriptor()
        println("         state before: $stateBeforeString")

        val freshCompareAppBefore = app.getEqualFreshCreator()()
        chain.applyReplayTo(freshCompareAppBefore)
        val replayedStateBeforeString = freshCompareAppBefore.exhaustiveStateDescriptor()
        println("replayed state before: $replayedStateBeforeString")

        chain.performConsensusRound(true)

        engine.recalculateTransactionDisplay()
        pipe.resetDrawBounds(engine)

        val stateAfterString = appDisplay.exhaustiveStateDescriptor()
        println("         state after:  $stateAfterString")

        val freshCompareAppAfter = app.getEqualFreshCreator()()
        chain.applyReplayTo(freshCompareAppAfter)

        val replayedStateAfterString = freshCompareAppAfter.exhaustiveStateDescriptor()
        println("replayed state after:  $replayedStateAfterString")

        val afterStorageRequirements = chain.calculateStorageRequirementsInBytes()


        val allStateStringsEqual = arrayOf(stateBeforeString, replayedStateBeforeString, stateAfterString, replayedStateAfterString).allEqual()

        val chainHashesValid = chain.validateHashChain()

        println("all state strings equal:  $allStateStringsEqual")
        println("chain hashes valid:  $chainHashesValid")
        println("storage-size(in bytes): $priorStorageRequirements -> $afterStorageRequirements   (difference:${priorStorageRequirements - afterStorageRequirements}")
        if(!allStateStringsEqual)
            throw SadException("STATES DO NOT EQUAL :(")
        if(!chainHashesValid)
            throw SadException("SQUASH RESULTED IN INCONSISTENT HASH STATE")
        if(afterStorageRequirements > priorStorageRequirements)
            throw SadException("SQUASH INCREASED DATA SIZE :(")
    }
    performConsensusJB.addActionListener {
        chain.performConsensusRound()

        engine.recalculateTransactionDisplay()
        if(pipe.userDrawBoundsMidOverride==null)
            pipe.resetDrawBounds(engine)
    }
    tickJB.addActionListener {
        engine.calculateTick()

        engine.recalculateTransactionDisplay()
        if(pipe.userDrawBoundsMidOverride==null)
            pipe.resetDrawBounds(engine)
    }
    epochNumberInputField.setDocument(IntegersOnlyDocument())
    epochNumberInputField.setText("50")
    epochJB.addActionListener {
        for(i in 0 until epochNumberInputField.getText().toInt())
            engine.calculateTick()

        engine.recalculateTransactionDisplay()
        if(pipe.userDrawBoundsMidOverride==null)
            pipe.resetDrawBounds(engine)
    }

    consensusEveryNTicksInputField.setDocument(IntegersOnlyDocument())
    consensusEveryNTicksInputField.setText("$consensusEveryNTicks")
    consensusEveryNTicksInputField.getDocument().addDocumentListener(object:DocumentListener {
        override fun changedUpdate(e: DocumentEvent?) = change()
        override fun insertUpdate(e: DocumentEvent?) = change()
        override fun removeUpdate(e: DocumentEvent?) = change()
        fun change() {
            if(consensusEveryNTicksInputField.getText().isNotEmpty())
                engine.consensusEveryNTick = Integer.parseInt(consensusEveryNTicksInputField.getText())
        }
    })

    squashEveryNConsensusRoundsInputField.setDocument(IntegersOnlyDocument())
    squashEveryNConsensusRoundsInputField.setText("$squashEveryNRounds")
    squashEveryNConsensusRoundsInputField.getDocument().addDocumentListener(object:DocumentListener {
        override fun changedUpdate(e: DocumentEvent?) = change()
        override fun insertUpdate(e: DocumentEvent?) = change()
        override fun removeUpdate(e: DocumentEvent?) = change()
        fun change() {
            if(squashEveryNConsensusRoundsInputField.getText().isNotEmpty())
                chain.squashEveryNRounds = Integer.parseInt(squashEveryNConsensusRoundsInputField.getText())
        }
    })

    addTxJB.addActionListener {
        val input = JOptionPane.showInputDialog(frame, "Please enter new tx in string form -\nshould roughly correspond to short tx identifier\nNote: does not work with all applications", "Enter new tx in short string representation", JOptionPane.QUESTION_MESSAGE)

        try {
            if (input == null)
                throw NullPointerException()
            else
                chain.commitToMemPool(app.createTxFrom(input))
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(frame, "Error - could not generate transaction\n${e.message}")
        }

        engine.recalculateTransactionDisplay()
        if(pipe.userDrawBoundsMidOverride==null)
            pipe.resetDrawBounds(engine)
    }

    applicationChooser.addActionListener {
        try {
            startApplicationChooser(frame, app, chain.squashEveryNRounds, engine.consensusEveryNTick)
            frame.dispose()
        } catch(e: CancellationException) {
        } catch(e: Exception) {
            JOptionPane.showMessageDialog(frame, "Error, could not create app.\nCheck input parameters and try again.\nError:\n${e.message}", "Could not create app", JOptionPane.ERROR_MESSAGE)
        }
    }


    //ui layout
    footerPanel.layout = BoxLayout(footerPanel, BoxLayout.X_AXIS)
    footerPanel.add(squashJB)
    footerPanel.add(performConsensusJB)
    footerPanel.add(tickJB)
    footerPanel.add(epochNumberInputField)
    footerPanel.add(epochJB)
    footerPanel.add(consensusEveryNTicksInputField)
    footerPanel.add(squashEveryNConsensusRoundsInputField)
    footerPanel.add(addTxJB)
    footerPanel.add(applicationChooser)
    frame.add(footerPanel, BorderLayout.SOUTH)





    //start everything
    frame.isVisible = true



    engine.recalculateTransactionDisplay()
    pipe.resetDrawBounds(engine)
}

private fun <T> Array<T>.allEqual(): Boolean = distinct().size == 1

typealias SadException = IllegalStateException