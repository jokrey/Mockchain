package jokrey.mockchain.visualization

import jokrey.mockchain.consensus.ConsensusAlgorithm
import jokrey.mockchain.consensus.ManualConsensusAlgorithm
import jokrey.mockchain.visualization.util.IntegersOnlyDocument
import jokrey.mockchain.visualization.util.LabeledInputField
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 *
 * @author jokrey
 */

fun getAppropriateConsensusControlPanel(frame: VisualizationFrame, consensus: ConsensusAlgorithm) :JPanel? {
    if(consensus is ManualConsensusAlgorithm) {
        return getManualConsensusControlPanel(frame, consensus)
    }
    return null
}

fun getManualConsensusControlPanel(frame: VisualizationFrame, consensus: ManualConsensusAlgorithm): JPanel {
    val squashJB = JButton("Squash")
    val performConsensusJB = JButton("Perform Consensus")
    val consensusEveryNTicksInputField = LabeledInputField("consensus every", 5)
    consensusEveryNTicksInputField.toolTipText = "consensus every X ticks(negative would be degree of randomness)"
    val squashEveryNConsensusRoundsInputField = LabeledInputField("squash every", 5)
    consensusEveryNTicksInputField.toolTipText = "squash every X consensus rounds (negative implies no-auto-squash)"


    squashJB.addActionListener {
        consensus.performConsensusRound(true)

        val chainHashesValid = frame.instance.chain.validateHashChain()
        if (!chainHashesValid)
            throw SadException("SQUASH RESULTED IN INCONSISTENT HASH STATE")

//        consensus.performConsensusRound(false) //required, otherwise the later performConsensusRound with squash will not have the same persistent state
//
//        val priorStorageRequirements = frame.instance.calculateStorageRequirementsInBytes()
//
//        val stateBeforeString = frame.app.exhaustiveStateDescriptor()
//        println("         state before: $stateBeforeString")
//
//        val freshCompareAppBefore = frame.app.getEqualFreshCreator()()
//        frame.instance.chain.applyReplayTo(freshCompareAppBefore)
//        val replayedStateBeforeString = freshCompareAppBefore.exhaustiveStateDescriptor()
//        println("replayed state before: $replayedStateBeforeString")
//
//        consensus.performConsensusRound(true)
//
//        frame.recalculateDisplay()
//
//        val stateAfterString = frame.app.exhaustiveStateDescriptor()
//        println("         state after:  $stateAfterString")
//
//        val freshCompareAppAfter = frame.app.getEqualFreshCreator()()
//        frame.instance.chain.applyReplayTo(freshCompareAppAfter)
//
//        val replayedStateAfterString = freshCompareAppAfter.exhaustiveStateDescriptor()
//        println("replayed state after:  $replayedStateAfterString")
//
//        val afterStorageRequirements = frame.instance.calculateStorageRequirementsInBytes()
//
//
//        val allStateStringsEqual = arrayOf(stateBeforeString, replayedStateBeforeString, stateAfterString, replayedStateAfterString).allEqual()
//
//        val chainHashesValid = frame.instance.chain.validateHashChain()
//
//        println("all state strings equal:  $allStateStringsEqual")
//        println("chain hashes valid:  $chainHashesValid")
//        println("storage-size(in bytes): $priorStorageRequirements -> $afterStorageRequirements   (difference:${priorStorageRequirements - afterStorageRequirements}")
//        if (!allStateStringsEqual)
//            throw SadException("STATES DO NOT EQUAL :(")
//        if (!chainHashesValid)
//            throw SadException("SQUASH RESULTED IN INCONSISTENT HASH STATE")
//        if (afterStorageRequirements > priorStorageRequirements)
//            throw SadException("SQUASH INCREASED DATA SIZE :(")
    }
    performConsensusJB.addActionListener {
        consensus.performConsensusRound(false)
    }

    consensusEveryNTicksInputField.setDocument(IntegersOnlyDocument())
    consensusEveryNTicksInputField.setText(consensus.consensusEveryNTick.toString())
    consensusEveryNTicksInputField.getDocument().addDocumentListener(object: DocumentListener {
        override fun changedUpdate(e: DocumentEvent?) = change()
        override fun insertUpdate(e: DocumentEvent?) = change()
        override fun removeUpdate(e: DocumentEvent?) = change()
        fun change() {
            if(consensusEveryNTicksInputField.getText().isNotEmpty())
                consensus.consensusEveryNTick = Integer.parseInt(consensusEveryNTicksInputField.getText())
        }
    })

    squashEveryNConsensusRoundsInputField.setDocument(IntegersOnlyDocument())
    squashEveryNConsensusRoundsInputField.setText(consensus.squashEveryNRounds.toString())
    squashEveryNConsensusRoundsInputField.getDocument().addDocumentListener(object: DocumentListener {
        override fun changedUpdate(e: DocumentEvent?) = change()
        override fun insertUpdate(e: DocumentEvent?) = change()
        override fun removeUpdate(e: DocumentEvent?) = change()
        fun change() {
            if(squashEveryNConsensusRoundsInputField.getText().isNotEmpty())
                consensus.squashEveryNRounds = Integer.parseInt(squashEveryNConsensusRoundsInputField.getText())
        }
    })

    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
    panel.add(squashJB)
    panel.add(performConsensusJB)
    panel.add(consensusEveryNTicksInputField)
    panel.add(squashEveryNConsensusRoundsInputField)
    return panel
}

typealias SadException = IllegalStateException