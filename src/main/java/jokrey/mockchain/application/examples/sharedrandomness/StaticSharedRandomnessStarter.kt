package jokrey.mockchain.application.examples.sharedrandomness

import jokrey.mockchain.storage_classes.NonPersistentStorage
import jokrey.mockchain.visualization.VisualizationFrame
import jokrey.utilities.base64Encode
import jokrey.utilities.decodeKeyPair
import jokrey.utilities.encodeKeyPair
import jokrey.utilities.misc.RSAAuthHelper
import jokrey.utilities.network.link2peer.P2Link
import jokrey.utilities.network.link2peer.node.core.NodeCreator
import jokrey.utilities.network.link2peer.rendevouz.RendezvousServer
import jokrey.utilities.network.link2peer.util.TimeoutException
import javax.swing.JOptionPane

/**
 *
 * @author jokrey
 */

fun main(args: Array<String>) {
    val ownName = if (!args.isEmpty()) args[0] else
        JOptionPane.showInputDialog("Enter own name")?: return
    if(ownName.isEmpty()) return

    val ownAddress = if (args.size > 1) args[1] else
        JOptionPane.showInputDialog("$ownName please:\nEnter <ip/dns>:<port> of own node (or just <port> if localhost)", "30104")
    val ownLink = P2Link.fromString(ownAddress)

    val ownKeyPairEncoded = if (args.size > 2) args[2] else
        JOptionPane.showInputDialog("$ownName please:\nEnter own encoded keypair or use the automatically generated key pair below", encodeKeyPair(RSAAuthHelper.generateKeyPair()))
    val ownKeyPair = decodeKeyPair(ownKeyPairEncoded)

    val contactNames = if (args.size > 5) args.toList().subList(5, args.size) else
        JOptionPane.showInputDialog("$ownName please:\nEnter list of names of well known contacts, separated by ','").split(",").map { it.trim() }
    if(contactNames.isEmpty()) return

    val showBlockchainUI = if (args.size > 4) args[4] == "yes" else
        JOptionPane.showConfirmDialog(null, "You will have a UI for the shared randomness, do you also want a blockchain UI?\nYou can always close it later.", "Show Blockchain UI?", JOptionPane.YES_NO_OPTION) == 0

    val rendezvousAddress = if (args.size > 3) args[3] else
        JOptionPane.showInputDialog("$ownName please:\nEnter <ip/dns>:<port> of rendezvous server", "lmservicesip.ddns.net:40000")
    var rendezvousLink = P2Link.fromString(rendezvousAddress)


    val node = NodeCreator.create(ownLink)

    var attemptResult: Array<RendezvousServer.IdentityTriple>
    do {
        attemptResult = try {
            RendezvousServer.rendezvousWith(node, rendezvousLink,
                    RendezvousServer.IdentityTriple(ownName, ownKeyPair.public.encoded, node.selfLink),
                    *contactNames.toTypedArray()
            )
        } catch (e: TimeoutException) {
            val newRendezvousAddress = JOptionPane.showInputDialog("Hi $ownName sorry, but:\nFailed to meet contacts at the rendezvous point within a certain time. Retry?\nEnter <ip/dns>:<port> of rendezvous server", rendezvousAddress)
            if(newRendezvousAddress == null) {
                node.close()
                throw IllegalStateException("failed to rendezvous")
            }
            rendezvousLink = P2Link.fromString(newRendezvousAddress)
            emptyArray()
        }
    } while(attemptResult.isEmpty())
    val contacts = attemptResult

    val app = StaticSharedRandomness(ownName, ownKeyPair,
            contacts.map { StaticSharedRandomnessParticipant(it.name, it.publicKey) } + StaticSharedRandomnessParticipant(ownName, ownKeyPair.public.encoded),
            1024, 1024
    )
    val instance = app.getInstance(node, store = NonPersistentStorage())

    println("found contacts = ${contacts.toList()}")
    println("contacts[0].address = ${contacts[0].address}")
    println("contacts[0].address.isHiddenLink = ${contacts[0].address.isHiddenLink}")

    instance.connect(contacts.map { it.address })

    if(showBlockchainUI) VisualizationFrame(instance)
    startAppUI(app, instance, ownName, ownKeyPair, contacts.map { Pair(it.name, base64Encode(it.publicKey)) }, allowEditingData = false, allowChoosingContacts = false)
}