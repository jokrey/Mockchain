package jokrey.mockchain.visualization

import jokrey.mockchain.Mockchain
import jokrey.mockchain.Nockchain
import jokrey.mockchain.application.examples.calculator.MashedCalculator
import jokrey.mockchain.application.examples.calculator.MultiStringCalculator
import jokrey.mockchain.application.examples.calculator.SingleStringCalculator
import jokrey.mockchain.application.examples.currency.Currency
import jokrey.mockchain.application.examples.currency.CurrencyWithHistory
import jokrey.mockchain.application.examples.sensornet.SensorNetAnalyzer
import jokrey.mockchain.application.examples.supplychain.SupplyChain
import jokrey.mockchain.consensus.ConsensusAlgorithmCreator
import jokrey.mockchain.consensus.ManualConsensusAlgorithmCreator
import jokrey.mockchain.consensus.ProofOfStaticStakeConsensusCreator
import jokrey.mockchain.consensus.SimpleProofOfWorkConsensusCreator
import jokrey.mockchain.storage_classes.NonPersistentStorage
import jokrey.mockchain.storage_classes.PersistentStorage
import jokrey.mockchain.storage_classes.StorageModel
import jokrey.mockchain.visualization.util.LabeledInputField
import jokrey.mockchain.visualization.util.UserAuthHelper
import jokrey.utilities.network.link2peer.P2Link
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.util.concurrent.CancellationException
import javax.swing.*
import javax.swing.filechooser.FileSystemView


val availableApps = arrayOf(
    SensorNetAnalyzer(),
    SingleStringCalculator(),
    MultiStringCalculator(1),
    MashedCalculator(1, maxDependencies = 1),
    Currency(),
    CurrencyWithHistory(),
    SupplyChain()
)

val defaultPair = UserAuthHelper.generateKeyPair() //todo - maybe slow
val availableConsensusAlgorithms = arrayOf(
        ManualConsensusAlgorithmCreator(),
        SimpleProofOfWorkConsensusCreator(4, byteArrayOf(1,2,3)),
        ProofOfStaticStakeConsensusCreator(1, arrayOf(defaultPair.public.encoded), defaultPair)
)

fun main() {
//    startMockVisualizationOf(SensorNetAnalyzer()) //works great, always
//    startMockVisualizationOf(SingleStringCalculator()) //works great, always
//    startMockVisualizationOf(SingleStringCalculator {it.amount.toInt() % 10 != 4}) //works great, always - even with a weird verify
//    startMockVisualizationOf(MultiStringCalculator(40)) //works great, always
//    startMockVisualizationOf(MashedCalculator(20, maxDependencies = 10)) //works great, always
//    startMockVisualizationOf(Currency()) //works great, always
//    startMockVisualizationOf(SupplyChain(55, 5)) //works great, always

//    buildNewInstanceConfiguration()

    VisualizationFrame(Nockchain(SingleStringCalculator(), P2Link.createPublicLink("localhost", 45221), consensus = ManualConsensusAlgorithmCreator(-1, Int.MAX_VALUE)))
    VisualizationFrame(Nockchain(SingleStringCalculator(), P2Link.createPublicLink("localhost", 45222), consensus = ManualConsensusAlgorithmCreator(-1, Int.MAX_VALUE)))
}

fun startApplicationChooserOnly(current: VisualizableApp? = null, frame: Component? = null) {
    val app = createChooser(availableApps, current, frame) as VisualizableApp
    VisualizationFrame(Mockchain(app))
}

fun buildNewInstanceConfiguration(currentApp: VisualizableApp? = null, currentConsensus: ConsensusAlgorithmCreator? = null, frame: Component? = null) {
    val app = createChooser(availableApps, currentApp, frame) as VisualizableApp
    val consensus = createChooser(availableConsensusAlgorithms, currentConsensus, frame) as ConsensusAlgorithmCreator
    val storageModel = startStorageModelChooser(frame)
    val networkLink = startNetworkChooser(frame)

    if(networkLink == null)
        VisualizationFrame(Mockchain(app, storageModel, consensus))
    else
        VisualizationFrame(Nockchain(app, networkLink, storageModel, consensus))
}
fun startMockVisualizationOf(app: VisualizableApp) {
    VisualizationFrame(Mockchain(app))
}

fun startNetworkChooser(frame: Component? = null): P2Link? {
    val result = JOptionPane.showInputDialog(frame, "If no actual network functionality is required: select 'Cancel', otherwise:\n" +
            "Enter public network link in the form <url>:<port>.\n" +
            "'localhost' is allowed, but note that it may prove difficult to find from another network.", "Choose Network Functionality and/or link", JOptionPane.QUESTION_MESSAGE)
            ?: return null

    val split = result.split(":")
    return P2Link.createPublicLink(split[0], split[1].toInt())
}


fun startStorageModelChooser(frame: Component? = null) : StorageModel {
    val result = JOptionPane.showOptionDialog(frame,
            "Choose Storage Model:\n" +
            "If you choose persistent, you will be asked for a directory in which a file(store.mockchain) will be created,\n" +
            "alternatively you can choose a previous file. In that case the data will be read from the selected file.\n\n" +
            "If you choose RAM storage, nothing of the session will be saved.",
            "Choose Storage Model", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
            arrayOf("Persistent", "Transient/RAM"), "Transient/RAM")
    return if(result == 0) {
        val jfc = JFileChooser(FileSystemView.getFileSystemView().homeDirectory)

        val returnValue = jfc.showOpenDialog(frame)
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            val selectedFile = jfc.selectedFile
            PersistentStorage(selectedFile, false)
        } else
            throw CancellationException()
    } else if(result == 1)
        NonPersistentStorage()
    else
        throw CancellationException()
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
                paramInput.setText(current.getCurrentParamContentForEqualCreation()[i])
    } else {
        nameChooser.selectedIndex = 0
    }

    val result = JOptionPane.showConfirmDialog(frame, content, "", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE)

    if(result != 0) throw CancellationException()

    val selectedApp = options[nameChooser.selectedIndex]
    val params = ArrayList<String>()
    for(paramInput in paramsPanel.components)
        if(paramInput is LabeledInputField)
            params.add(paramInput.getText())

    return selectedApp.createNewInstance(*params.toTypedArray())
}