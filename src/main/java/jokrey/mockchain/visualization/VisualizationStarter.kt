package jokrey.mockchain.visualization

import jokrey.mockchain.Mockchain
import jokrey.mockchain.Nockchain
import jokrey.mockchain.application.examples.calculator.MashedCalculator
import jokrey.mockchain.application.examples.calculator.MultiStringCalculator
import jokrey.mockchain.application.examples.calculator.SingleStringCalculator
import jokrey.mockchain.application.examples.currency.Currency
import jokrey.mockchain.application.examples.currency.CurrencyWithHistory
import jokrey.mockchain.application.examples.sensornet.SensorNetAnalyzer
import jokrey.mockchain.application.examples.sharedrandomness.SharedRandomness
import jokrey.mockchain.application.examples.supplychain.SupplyChain
import jokrey.mockchain.consensus.ConsensusAlgorithmCreator
import jokrey.mockchain.consensus.ManualConsensusAlgorithmCreator
import jokrey.mockchain.consensus.ProofOfStaticStakeConsensusCreator
import jokrey.mockchain.consensus.SimpleProofOfWorkConsensusCreator
import jokrey.mockchain.storage_classes.NonPersistentStorage
import jokrey.mockchain.storage_classes.PersistentStorage
import jokrey.mockchain.storage_classes.StorageModel
import jokrey.utilities.misc.RSAAuthHelper
import jokrey.utilities.network.link2peer.P2Link
import java.awt.Component
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
    SupplyChain(),
    SharedRandomness()

    /*
    todo Blockchain application perfectly usable with Squash:
        * Shipment tracker - only the receiver of a shipment can create a signed transaction which ends the sequence
            (Similar to the supply chain, but from a consumer/customer benefit position)
        * Bitcoin squash spent transactions - for a better, more intelligent pruning
        * Chat(2 way) - each chat message from a single source uses sequence-parts, a sequence-end can only be attached by the other party (that way it is ensured that a message is seen before deleted
    todo General Blockchain application ideas:
         Blockchain for semi centralized qualification tracking:
      Use case:
          Qualifiers can apply to be allowed to credit qualifications to people
          Anyone can run a query with a name, a date of birth and birth location - and a alleged qualification
                The system will answer yes or no - potentially who credited the qualification
      The full blockchain has to be hidden so private data is not leaked - or a key system
          i.e. every record is optionally encrypted with a simple key it has to also be added to the query

     */
)

val defaultPair = RSAAuthHelper.generateKeyPair() //todo - maybe slow on some machines
val availableConsensusAlgorithms = arrayOf(
        ManualConsensusAlgorithmCreator(),
        SimpleProofOfWorkConsensusCreator(4, byteArrayOf(1,2,3)),
        ProofOfStaticStakeConsensusCreator(1, listOf(defaultPair.public.encoded), defaultPair)
)

fun main() {
//    startMockVisualizationOf(SensorNetAnalyzer()) //works great, always
//    startMockVisualizationOf(SingleStringCalculator()) //works great, always
//    startMockVisualizationOf(SingleStringCalculator {it.amount.toInt() % 10 != 4}) //works great, always - even with a weird verify
//    startMockVisualizationOf(MultiStringCalculator(40)) //works great, always
//    startMockVisualizationOf(MashedCalculator(20, maxDependencies = 10)) //works great, always
//    startMockVisualizationOf(Currency()) //works great, always
//    startMockVisualizationOf(SupplyChain(55, 5)) //works great, always

    buildNewInstanceConfiguration()

//    VisualizationFrame(Nockchain(SingleStringCalculator(), P2Link.Local.forTest(45221).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator(-1, Int.MAX_VALUE)))
//    VisualizationFrame(Nockchain(SingleStringCalculator(), P2Link.Local.forTest(45222).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator(-1, Int.MAX_VALUE)))
}

fun startApplicationChooserOnly(current: VisualizableApp? = null, frame: Component? = null) {
    val app = createChooser(availableApps, current, frame) as VisualizableApp
    VisualizationFrame(Mockchain(app))
}

fun buildNewInstanceConfiguration(currentApp: VisualizableApp? = null, currentConsensus: ConsensusAlgorithmCreator? = null, frame: Component? = null) {
    val app = createChooser(availableApps, currentApp, frame) as VisualizableApp
    val instance = startChainInstanceChooser(app, currentConsensus, frame)
    VisualizationFrame(instance)
}

fun startChainInstanceChooser(app: VisualizableApp, currentConsensus: ConsensusAlgorithmCreator? = null, frame: Component? = null): Mockchain {
    val consensus = createChooser(availableConsensusAlgorithms, currentConsensus, frame) as ConsensusAlgorithmCreator
    return startChainInstanceChooser(frame, app, consensus)
}

fun startChainInstanceChooser(frame: Component? = null, app: VisualizableApp, consensus: ConsensusAlgorithmCreator): Mockchain {
    val storageModel = startStorageModelChooser(frame)
    val networkLink = startNetworkChooser(frame)

    return if (networkLink == null)
        Mockchain(app, storageModel, consensus)
    else
        Nockchain(app, networkLink, storageModel, consensus)
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
    return P2Link.Direct(split[0], split[1].toInt())
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