package jokrey.mockchain.network

import jokrey.utilities.network.link2peer.P2LNode
import jokrey.utilities.network.link2peer.P2Link

class ChainNode(link: P2Link, peerLimit:Int) {
    val p2Node = P2LNode.create(link, peerLimit)

    init {

    }
}