package jokrey.mockchain.application.examples.sharedrandomness

import jokrey.mockchain.storage_classes.NonPersistentStorage
import jokrey.mockchain.visualization.VisualizationFrame
import jokrey.utilities.base64Encode
import jokrey.utilities.decodeKeyPair
import jokrey.utilities.encodeKeyPair
import jokrey.utilities.misc.RSAAuthHelper
import jokrey.utilities.network.link2peer.P2LNode
import jokrey.utilities.network.link2peer.P2Link
import jokrey.utilities.network.link2peer.node.core.NodeCreator
import jokrey.utilities.network.link2peer.rendezvous.IdentityTriple
import jokrey.utilities.network.link2peer.rendezvous.RendezvousServer
import java.util.*
import javax.swing.JOptionPane
import kotlin.system.exitProcess

/**
 * TODO - problem: if a node disconnects there is no way to get back in...
 *
 * TODO - show a list of possible contacts instead of forcing the user to type them in (like a lobby)
 *
 *
 * @author jokrey
 */

fun main(args: Array<String>) {
    var node : P2LNode? = null
    try {
        val ownName = if (!args.isEmpty()) args[0] else
            JOptionPane.showInputDialog("Enter own name") ?: return
        if (ownName.isEmpty()) return

        val ownAddress = if (args.size > 1) args[1] else
            JOptionPane.showInputDialog("$ownName please:\nEnter <ip/dns>:<port> of own node (or <port> if localhost)", "30104")
        val ownLink = P2Link.from(if (ownAddress.contains(":")) ownAddress else "$ownName[local=$ownAddress]")

        val ownKeyPairEncoded = if (args.size > 2) args[2] else
            JOptionPane.showInputDialog("$ownName please:\nEnter own encoded keypair or use the automatically generated key pair below", encodeKeyPair(RSAAuthHelper.generateKeyPair()))
        val ownKeyPair = decodeKeyPair(ownKeyPairEncoded)

        val contactNames = if (args.size > 5) args.toList().subList(5, args.size) else
            JOptionPane.showInputDialog("$ownName please:\nEnter list of names of well known contacts, separated by ','").split(",").map { it.trim() }
        if (contactNames.isEmpty() || contactNames[0].isEmpty()) return

        val showBlockchainUI = if (args.size > 4) args[4] == "yes" else
            JOptionPane.showConfirmDialog(null, "You will have a UI for the shared randomness, do you also want a blockchain UI?\nYou can always close it later.", "Show Blockchain UI?", JOptionPane.YES_NO_OPTION) == 0

        val rendezvousAddress = if (args.size > 3) args[3] else
            JOptionPane.showInputDialog("$ownName please:\nEnter <ip/dns>:<port> of rendezvous server", "lmservicesip.ddns.net:40000")
        var rendezvousLink = P2Link.from(rendezvousAddress) as P2Link.Direct


        node = NodeCreator.create(ownLink)




        val selfIdentity = IdentityTriple(ownName, ownKeyPair.public.encoded, node.selfLink)

        RendezvousServer.register(node, rendezvousLink, selfIdentity).waitForIt(10000)

        val connectedPeers = ArrayList<IdentityTriple>()
        val remainingNames = contactNames.toMutableList()
        main@ while (connectedPeers.size < contactNames.size) {
            val newlyFound = RendezvousServer.request(node, rendezvousLink, *remainingNames.toTypedArray())
            for (it in newlyFound) {
                println("attempt to = $it")
                val success = node.establishConnection(it.link).getOrNull(5000)
                println("connection to \"${it.name}\" - success = ${success} ")
                if (success != null && success) {
                    connectedPeers.add(it)
                    remainingNames.remove(it.name)
                }
            }

            if (remainingNames.isNotEmpty()) {
                val newRendezvousAddress = JOptionPane.showInputDialog("Hi $ownName sorry, but:\nFailed to meet contacts at the rendezvous point within a certain time. Retry?\nEnter <ip/dns>:<port> of rendezvous server", rendezvousAddress)
                val newRendezvousLink = P2Link.from(newRendezvousAddress) as P2Link.Direct
                if (newRendezvousAddress == null) {
                    node.close()
                    exitProcess(1)
                } else if(newRendezvousLink != rendezvousLink) {
                    node.disconnectFrom(rendezvousLink)
                    rendezvousLink = newRendezvousLink
                    RendezvousServer.register(node, rendezvousLink, selfIdentity).waitForIt(10000)
                }
            }
        }



        val contacts = connectedPeers

        val app = StaticSharedRandomness(ownName, ownKeyPair,
                contacts.map { StaticSharedRandomnessParticipant(it.name, it.publicKey) } + StaticSharedRandomnessParticipant(ownName, ownKeyPair.public.encoded),
                1024, 1024
        )
        val instance = app.getInstance(node, store = NonPersistentStorage())

        println("found contacts = ${contacts.toList()}")
        println("contacts[0].address = ${contacts[0].link}")
        println("contacts[0].address.isHiddenLink = ${contacts[0].link.isRelayed}")

        if (showBlockchainUI) VisualizationFrame(instance)
        startAppUI(app, instance, ownName, ownKeyPair, contacts.map { Pair(it.name, base64Encode(it.publicKey)) }, allowEditingData = false, allowChoosingContacts = false)
    } catch (t: Throwable) {
        t.printStackTrace()
        node?.close()
        exitProcess(1)
    }
}