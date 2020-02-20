package jokrey.mockchain.visualization.util

import java.awt.Font
import java.awt.event.ActionListener
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.Document

class LabeledInputField(labelText: String, tf_columns: Int) : JPanel() {
    private val l: JLabel
    private val tf: JTextField

    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        l = JLabel(labelText)
        tf = JTextField(tf_columns)
        add(l)
        add(tf)
    }

    override fun setFont(arg0: Font) {
        super.setFont(arg0)
        if(l == null) return //I know kotlin thinks this is bs, but it is not. It is required since setFont is called before l is initialized
        l.font = arg0
        tf.font = arg0
    }

    fun setLabelText(s: String) {
        l.text = s
    }

    fun setText(text: String?) {
        tf.text = text
    }
    fun getText() : String = tf.text

    fun setDocument(d: Document) {
        tf.document = d
    }
    fun getDocument() : Document {
        return tf.document
    }

    fun addActionListener(al: ActionListener) {
        tf.addActionListener(al)
    }

    fun addDocumentListener(al: ActionListener) {
        tf.document.addDocumentListener(object : DocumentListener {
            override fun removeUpdate(arg0: DocumentEvent) {
                al.actionPerformed(null)
            }

            override fun insertUpdate(arg0: DocumentEvent) {
                al.actionPerformed(null)
            }

            override fun changedUpdate(arg0: DocumentEvent) {
                al.actionPerformed(null)
            }
        })
    }

    fun addTextChangeListener(function: () -> Unit) {
        getDocument().addDocumentListener(object: DocumentListener {
            override fun changedUpdate(e: DocumentEvent?) = function()
            override fun insertUpdate(e: DocumentEvent?) = function()
            override fun removeUpdate(e: DocumentEvent?) = function()
        })
    }
}