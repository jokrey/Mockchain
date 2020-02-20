package jokrey.mockchain.visualization

import jokrey.mockchain.network.ChainNode
import jokrey.utilities.network.link2peer.P2Link
import java.awt.BorderLayout
import java.awt.Color
import java.awt.event.ActionListener
import javax.swing.*

/**
 *
 * @author jokrey
 */

internal fun getAppropriateNetworkControlPanel(frame: VisualizationFrame, node: ChainNode?) :JPanel? {
    if(node != null) {
        return getNetworkControlPanel(frame, node)
    }
    return null
}

private fun getNetworkControlPanel(frame: VisualizationFrame, node: ChainNode): JPanel {
    val whoAmILabel = JLabel(node.p2lNode.selfLink.toString())

    val disconnectAll = JButton("DisconnectFromAll")

    val connectToPanel = JPanel(BorderLayout())
    val connectToInputField = JTextField(20)
    val connectToButton = JButton("connect")
    connectToInputField.toolTipText = "connect to (<url>:<port>)"
    connectToPanel.border = BorderFactory.createMatteBorder(0, 2, 0, 2, Color.black)
    connectToPanel.add(connectToInputField, BorderLayout.CENTER)
    connectToPanel.add(connectToButton, BorderLayout.EAST)

    val showConnectionsButton = JButton("show connections(0)")

    node.p2lNode.addConnectionDroppedListener {
        showConnectionsButton.text = "show connections(${node.p2lNode.establishedConnections.size})"
    }
    node.p2lNode.addConnectionEstablishedListener { t, u ->
        showConnectionsButton.text = "show connections(${node.p2lNode.establishedConnections.size})"
    }

    disconnectAll.addActionListener {
        node.p2lNode.disconnectFromAll()
    }

    val connectToCallback = ActionListener {
        val split = connectToInputField.text.split(":")
        node.connect(P2Link.createPublicLink(split[0], split[1].toInt()))
        connectToInputField.text = ""
    }
    connectToButton.addActionListener(connectToCallback)
    connectToInputField.addActionListener(connectToCallback)

    showConnectionsButton.addActionListener {

    }

    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
    panel.add(whoAmILabel)
    panel.add(disconnectAll)
    panel.add(connectToPanel)
    panel.add(showConnectionsButton)

    return panel
}
