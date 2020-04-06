package jokrey.mockchain.visualization

import jokrey.mockchain.consensus.*
import jokrey.mockchain.visualization.util.IntegersOnlyDocument
import jokrey.mockchain.visualization.util.LabeledInputField
import jokrey.utilities.base64Decode
import jokrey.utilities.base64Encode
import java.lang.Exception
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JPanel

/**
 *
 * @author jokrey
 */

fun getAppropriateConsensusControlPanel(frame: VisualizationFrame, consensus: ConsensusAlgorithm) :JPanel? {
    if(consensus is ManualConsensusAlgorithm) {
        return getManualConsensusControlPanel(frame, consensus)
    } else if(consensus is ProofOfStaticStake) {
        return getProofOfStaticStakeControlPanel(frame, consensus)
    } else if(consensus is ProofOfWorkConsensus) {
        return getProofOfWorkControlPanel(frame, consensus)
    }
    return null
}

fun getProofOfWorkControlPanel(frame: VisualizationFrame, consensus: ProofOfWorkConsensus): JPanel {
    val panel = JPanel()

    val difficultyInputField = LabeledInputField("difficulty ([1-20]): ", 3)
    difficultyInputField.toolTipText = "enter difficulty ( int between 1 and 20 ) - HAS TO BE THE SAME FOR ALL NODES"
    difficultyInputField.addTextChangeListener {
        if(difficultyInputField.text.isNotEmpty())
            consensus.difficulty = difficultyInputField.text.toInt()
    }

    val minerIdentityInputField = LabeledInputField("miner identity (base64): ", 5)
    minerIdentityInputField.toolTipText = "enter miner identity as a base64 encoded string"
    minerIdentityInputField.addTextChangeListener {
        try {
            consensus.minerIdentity = base64Decode(minerIdentityInputField.text)
            minerIdentityInputField.setLabelText("miner identity (base64)")
        } catch(e: Exception) {
            minerIdentityInputField.setLabelText("miner identity (base64(!))")
        }
    }

    val requestSquashCheckbox = JCheckBox("request squash")
    requestSquashCheckbox.addItemListener {
        consensus.requestSquash = requestSquashCheckbox.isSelected
    }

    panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
    panel.add(difficultyInputField)
    panel.add(minerIdentityInputField)
    panel.add(requestSquashCheckbox)

    difficultyInputField.text = (consensus.difficulty.toString())
    minerIdentityInputField.text = (base64Encode(consensus.minerIdentity))
    requestSquashCheckbox.isSelected = consensus.requestSquash

    //todo - information such as: currently staged txs, current nonce

    return panel
}

fun getProofOfStaticStakeControlPanel(frame: VisualizationFrame, consensus: ProofOfStaticStake): JPanel {
    val panel = JPanel()

    //todo - displays of the current state of the consensus algorithm - like who's turn it is, etc..

    return panel
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
    consensusEveryNTicksInputField.text = (consensus.consensusEveryNTick.toString())
    consensusEveryNTicksInputField.addTextChangeListener {
        if(consensusEveryNTicksInputField.text.isNotEmpty())
            consensus.consensusEveryNTick = Integer.parseInt(consensusEveryNTicksInputField.text)
    }

    squashEveryNConsensusRoundsInputField.setDocument(IntegersOnlyDocument())
    squashEveryNConsensusRoundsInputField.text = (consensus.squashEveryNRounds.toString())
    squashEveryNConsensusRoundsInputField.addTextChangeListener {
        if(squashEveryNConsensusRoundsInputField.text.isNotEmpty())
            consensus.squashEveryNRounds = Integer.parseInt(squashEveryNConsensusRoundsInputField.text)
    }

    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
    panel.add(squashJB)
    panel.add(performConsensusJB)
    panel.add(consensusEveryNTicksInputField)
    panel.add(squashEveryNConsensusRoundsInputField)
    return panel
}

typealias SadException = IllegalStateException