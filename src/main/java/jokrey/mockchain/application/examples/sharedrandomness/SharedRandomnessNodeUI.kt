package jokrey.mockchain.application.examples.sharedrandomness

import jokrey.mockchain.Mockchain
import jokrey.mockchain.Nockchain
import jokrey.mockchain.consensus.ManualConsensusAlgorithmCreator
import jokrey.mockchain.visualization.VisualizationFrame
import jokrey.mockchain.visualization.util.LabeledInputField
import jokrey.utilities.*
import jokrey.utilities.network.link2peer.P2LNode
import jokrey.utilities.network.link2peer.P2Link
import jokrey.utilities.network.link2peer.node.core.NodeCreator
import jokrey.utilities.network.link2peer.rendezvous.IdentityTriple
import jokrey.utilities.network.link2peer.rendezvous.RendezvousServer
import jokrey.utilities.network.link2peer.util.P2LFuture
import jokrey.utilities.network.link2peer.util.P2LThreadPool
import jokrey.utilities.simple.data_structure.pairs.MutablePair
import java.awt.*
import java.awt.event.ActionEvent
import java.net.URI
import java.security.KeyPair
import javax.swing.*


/**
 *
 * @author jokrey
 */
fun main() {
    //todo - a different shared randomness that requires each participant to operate a node - and runs its own consensus algorithm

    //WHICH CONSENSUS DO I DO? Just active a round whenever something is new and at least 1 second has passed locally? + round robin
                //Basically a triggered proof-of-static-stake
                //that creates a ton of blocks though, right?
                    //But that is fine.

//    val instance = startChainInstanceChooser(app, ManualConsensusAlgorithmCreator())

//    val chosenOwnName = JOptionPane.showInputDialog(null, "Choose your name:")
//    if(chosenOwnName==null) {JOptionPane.showMessageDialog(null, "Please select a name next time");return}
//
//    startAppUI(app, instance, chosenOwnName, RSAAuthHelper.generateKeyPair(), listOf())


    val knownIdentities = listOf(
            Pair("Peter", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwOqQbaoP3JYga9a67p7z6V7Q8Xd9FrpzB153ILY0LoqKeTHaj60CG4ZmImHBEQcNHkQsdqELjhFGYCwCxUU9A0tczJ8eTPC8uPlbuk1ke0OJ19xG2VXcacjsQDdmEOc3a88EBf1Y/4GTi/D0oUWFaAHWK2opfvEkPvrhpOHdgV/69CLQ3QpYypR+Fr1WcsqZHjRikcfDSD7hSZVv4RluMFx9AL2cW1X2/yx7H41+jsIbi14xhSDMJMTFFfeEVbVTFgy+E90d7rejaLRoSEPb32x1HRbb8/iEryM5C9LoljGmLkJd99XKkhD55xG9Eopx5qLnPwqCAJvWzLMAHnjHBwIDAQAB:MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDA6pBtqg/cliBr1rrunvPpXtDxd30WunMHXncgtjQuiop5MdqPrQIbhmYiYcERBw0eRCx2oQuOEUZgLALFRT0DS1zMnx5M8Ly4+Vu6TWR7Q4nX3EbZVdxpyOxAN2YQ5zdrzwQF/Vj/gZOL8PShRYVoAdYrail+8SQ++uGk4d2BX/r0ItDdCljKlH4WvVZyypkeNGKRx8NIPuFJlW/hGW4wXH0AvZxbVfb/LHsfjX6OwhuLXjGFIMwkxMUV94RVtVMWDL4T3R3ut6NotGhIQ9vfbHUdFtvz+ISvIzkL0uiWMaYuQl331cqSEPnnEb0SinHmouc/CoIAm9bMswAeeMcHAgMBAAECggEBAKplBJi4YzY1LAHUMlxd7ZatduQw5D3VBZD2sUYlaUXKfLC7hg7tgzUIquGncj41+jJHiPZnHKupOn3roa7YjyF/yUG7MapH4ImJRqnxfdUaPIB7QeDpY7vUCkhWJkK710nUGfuoYJmdu9MZSxm/LCxHowHJzUkgeSFfuzpFfb6snTy+GRhdoROxcp8RgPKQDVnxrUGylB1OpUOaqfHpO5YBQno7Vt0MlZJR4hMfHuCTdyze/2M+b9v3cSMHH0RQH7MTbSdDvzUZfTq+V7zcXvaUbAQ+EQ5fumXQXzQePO08byKETg8OpWkZJeY5wO74O+TYZ2WCjX4mZuniQlRjt1ECgYEA5xwmYQq2SEvIZFMK+Bq7uHalXxzYlwz/zIm2ayu7igDbP0Zv62WrlIgCkvp/Sonj0Oc1R8z62XaX3kbOCK5jeHKBOzqOmbiLwiZ/XWA1CmIJMV8DD/tDael0nvsitjGXjjvmm3O++2OqZfCICPjfKRNLmwpPhOBsf7hUbK9TFG0CgYEA1bFjuqXjVnZG+T08JYYJ6DVJMeDBVIMOLLfgKVC0rjC9SZmWNNGZJJ4Q+JnNxsRsHyzyT9MZgc7TRUfA0/ke6C8dFukeFpDLPQGoktAfdyAX/cRZGaeqlaGAsSF19z15f4jpQlZkknz6GU3Ce6zMzZe3OqqyLv/ZIeJ4QsGeGMMCgYAkyZZSXCIn3+hGD/HvDFJVSo2IVk8jvC37oPAonw17Kie8Krol/kkRm5TNUJJyiwB4gFU62KYVd4s1FpA1UY0D3zYy9187mOSmQvqDIo1O2cwcz8LtCFHyyfaGV/NujPZS7bYHiKUd3v+Auojs5LChGTEvvLRrsk2TBwRpSH8xAQKBgBWSitbU2FZqKlAO9ntzRJzEhFccsWeus0egaGjDVPogwXsknh1G64bezifKnxNp0OB00SFt1i1ci8d6ruS3SX93AiDF99ufUmUePb5UdFi6TLG5mKUWYAoq+6rmDdqfwhw13hZsUkrXgwf66Z9CmopGvqCVitdjzK+3BRz4HtWxAoGBAIq61bVhmBxwJCKTw2txSLw2bgOP62RITHFxAfweBbxDeAU4rg8hbPuxj9yhlQsYnMEUPfvxh65FwMiRyMj+3S5J/NxaspioLYGjqZfe+Fz05IrVjflJ/b5G7D0eV3duuunBbTOEFQtn86DYZ8GWgvn6cy+2AaiA/YiXYzYpqPuu"),
            Pair("Otto", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApUcr6NOugg720mTiPbbbScSo140kz/hLIDFTt3HogfvW5ArsV0xvoM3SfGsX+Bw2F5Hzk415xkV06kBA8zNhNWO5q9BB+uwwlcQVXYTZvNuobC4aud778n0YROWExY+zL+xXMcNTdqywUkSoVo4/CP9MgVwd+Co9u2gZXXxPdhV+/+KhGg2qox8LWLR8uJMPTlfIqNj7vGQdBZpvkyArON2ESd0A1//I07oRB1RCPc66Wm/gmxh2oA8eE4N+9NDSU2oMyun30MNI/BVFtF+W1IT5h6IR38iYKVn5u74IC8exEeFv35eiLMHgi/SGiF8Hli/AlhhFQRCDsQB51uCsQwIDAQAB:MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQClRyvo066CDvbSZOI9tttJxKjXjSTP+EsgMVO3ceiB+9bkCuxXTG+gzdJ8axf4HDYXkfOTjXnGRXTqQEDzM2E1Y7mr0EH67DCVxBVdhNm826hsLhq53vvyfRhE5YTFj7Mv7Fcxw1N2rLBSRKhWjj8I/0yBXB34Kj27aBldfE92FX7/4qEaDaqjHwtYtHy4kw9OV8io2Pu8ZB0Fmm+TICs43YRJ3QDX/8jTuhEHVEI9zrpab+CbGHagDx4Tg3700NJTagzK6ffQw0j8FUW0X5bUhPmHohHfyJgpWfm7vggLx7ER4W/fl6IsweCL9IaIXweWL8CWGEVBEIOxAHnW4KxDAgMBAAECggEALwS0RSQTPQSsyuXQRuZCNBYyQj/w/QkRtjLSMhnBn1jZpT5GRf+EsiZbfvGoe/jqmoH23T8eKX2Q6SMmVwmC2gFozKwOWSfgGnsR6OzmVIfYvg3PpJj+69kSkmcJAnsC6ts9YvbCQ7yU3JKToSwOWqzmQtbF39eEgE/5B1NZ96loV62EAf9RM5tSKkicEUg83LqylJZmuDQkQYDcokbU9V7JekyTxBIzTv9st/9cvwbZ3U5rR0AdiCmBr+LA+LlX68VxDo6SZ/JsnmNJWEl4uTzFK54KLX4Ng6msKfCpMki5ZZ8NDglNySJgACUNfCFz7t0240BItu3wmpKueO/KQQKBgQDhM5x+xXfD5wGyh2L7QaUtwmP519lPA7GTJfLAepX62iYuUmbgrcWvBqyLsLiW1EeaHSWHDa1dh9DEErPDtzeQcQJT9VESKELaNMQW09tbGzo/Mx5Rtfc0AjwCzPwswzgL5hSrJKW/KYCZe+8ebOyx/rqRVB4vTvwyIJt2a9rkawKBgQC74Z0jRVN0V98YClidELY2uGRNJqqbcIsKwd72gjh5CAzCGMWgg1diOqSs1iPe19IHoaHj8APUmdJ0rVI3+tWcG+RHJFz2Q/rLlrX41ntSXsHFCPNGu7FGQCxpdEQJtWTPGcdZZObZejfCm0sjN2rdI6mc4RpFhT0iKGDvRCkNiQKBgC3WDmUzFfRWoV7P9ZKEQvV0Wlrw1vchHlR/5c/NY5diLWFCPlQ+qjy8lAP+nSN943D3u7qoSv/9c71kvRf5w6JvjfS+upiCf1DgaoTm6/+4I/vXELW63qzEQ6iiRjVqKo8pbk2DMQUekmEq+3lq3CZCXYDU6Svh3KzrPBk3TJ8vAoGBAKttBuiIt8W+63LO9d2RwwAYrIPslNwxCtys2hhH5ukf3Cw5WBDF5jRdV6XP2XjZqOyHoOQOOiCAnZMSFaO4PbErjdUPq7aTfkDGaZD7ehhFFz4FlZtjZDO6GAu8JtxI4wtH9SlutGeYaUoqUZt3VA0kHf1jMopeMNJ6zz9hDKgpAoGAFO7EL/W/z1GCiOoBTgvXAeLpc+GvitwYxyoYPb7r4evjhnLNhVV92x1C+zt9jXprhBHlZxFfU9E+ITu7fGmYvBpxkbabVg6gCB6bs49N162uOrBfnmjHoB+87CSp3QWRvmVJNqXLEhDUNvm6/WoFQ3RBqcDJDU6UmqoZRowkCFM="),
            Pair("Gertrud", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAh+wh7AozvvYY0PrZXoTVK9fEE7YcIWrXKQtnrEZzoXeeAhS0AWue6uEmQsE2IbNV/J2I0O4bV+KXMk9URm9eO4XW0PexHHc9vS1avBu+p2VjHbGhJSAJz33uHwtMSi/lfe7ZnX/UqbV5XMVEXrPVzQrwvPUHpM647wnfdRq2/hzUUek/s9vNAu43KqDWBgpFmiBamW4sW4gn9kDjo3PzcyHnAMk1k6Xf3fEMDSpyp2EUpxTvgtx9gmnnR9AN/Cjm9g/p5AsVHZ1YHFCB6V4rm+GLMft6hTmiWG2gep94fxew29OnREOoCcQmc/eUPkgyuhMcoO4s/atNePfJ4pveAQIDAQAB:MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCH7CHsCjO+9hjQ+tlehNUr18QTthwhatcpC2esRnOhd54CFLQBa57q4SZCwTYhs1X8nYjQ7htX4pcyT1RGb147hdbQ97Ecdz29LVq8G76nZWMdsaElIAnPfe4fC0xKL+V97tmdf9SptXlcxURes9XNCvC89QekzrjvCd91Grb+HNRR6T+z280C7jcqoNYGCkWaIFqZbixbiCf2QOOjc/NzIecAyTWTpd/d8QwNKnKnYRSnFO+C3H2CaedH0A38KOb2D+nkCxUdnVgcUIHpXiub4Ysx+3qFOaJYbaB6n3h/F7Db06dEQ6gJxCZz95Q+SDK6Exyg7iz9q01498nim94BAgMBAAECggEAR5AKmCUS84LMtBKuqXYUaj3yzVH/Y5TF7aVEk06QiL3a4kuWLn1EMXQTWegyIPIz3onuw9npaY8yfdmIjIEMQxiHboRKqqsZRWYAtLOC4M2frr2cE1jX8XfjDFM9en3XPUOpLaRlCmkymaZ/BcF3Wrpc34++04XHlotDLHvBRu6QaMEgeiTprMX/Op06Q1AahVPSUqAOPAim912Se5q1UdqalrrKNpDcpgNqf62uRcMmstd6it/hlIvoDV4n4ypuIWZTBGvdQj4VLlFlc4c5l6VEies4H9Otzv6TuC8lAmUHNLnFj2b6mrjW0jPTREdED7qkvwHiEOh5DfrvhAe3AQKBgQC76s2OnaHzVn+Vc1FXZoU4e/DBacxmqNq1a31cURELLVksRAHfabBGtWX1bIeNeA6ko/Z4E/0bbHoev5lbPZzv9+81pmyXp3XrXyuYNRdc8e5ZTTVG/+PnhUcjzg1CAUKi0TBtpz+zFK5U7jUY9jvSzNRnuAOU8e6Jf3gm5Vb1kQKBgQC5KthnuT7YQkIZ/87FrEVIyZbp3eB1T2MTxNR5p4FuNwU6169ydy93cGAr/n7WloUox8V6RaQQKr5y0sKo6VaiICQ3udUc2utTc4ETe3wivUcvu7fUdUYT9EoDKBRu+FK9Q1IQa6qQDQQtz08aFN/CrTCMG1NFZbiU1F2QX/FpcQKBgQCVCq8MPRP01xcL5tGN+38QBKU4EfyPM797goyECrv03HvMcwf1NXMdMcRzOifs2Vrr1Cuoo1ntRUU6XAZ66kwtu7ybFastQSFylCIUb49fJXdAls75x/zvZLK+wC+duTgrwLSjU7JfC7kVHXU5nhpmoBSbSsR0fsoNfe9DEkS9MQKBgQCJohAyoN3WjwFlI+BEy/S/0p+q+7HgYH7Lbe1k853gF2N6xmDxmyecBtplOQh8ZmtZ0Yu2g9cb8TmYTZJFTROI9I0XIrkGdq6eW+dgXNP7Wmd0Unqkn/rT0CvHRt5RUaDmbwirjeu8oQAvML2iLEvZ/zNroM/3cFGPxn45Vycw8QKBgAi/iQogPwDIt6OZgyE8VqHM29LSDh6WjvQZ78g7d/joeTuNlnM2ZAR07/9f+8sJsJuAtwV+uvS4toSysqe720/2tn6zk+qHm/VnO+dnIAl8AwKUwkzLOJ7/uiA0R2bxru4mny7sHjreAUs9m23a6sRvDxEpZ2hqcjl5KglKAjD1")
    )

//    startLocalNodeWithSingleUI(knownIdentities)
//    startLocalNodeWithUIForEachIdentity(knownIdentities)
//    startConnectedNodesWithIdentities(knownIdentities)
    startNodesConnectedThroughRendezvous(knownIdentities)
}

fun startNodesConnectedThroughRendezvous(knownIdentities: List<Pair<String, String>>) {
    val rendezvousLink = P2Link.Local.forTest(40000).unsafeAsDirect()
    val relayServer = RendezvousServer(rendezvousLink)

    val nodes = listOf(
            NodeCreator.create(P2Link.Local.forTest(30141).unsafeAsDirect()),
            NodeCreator.create(P2Link.Local.forTest(30142).unsafeAsDirect()),
            NodeCreator.create(P2Link.Local.forTest(30143).unsafeAsDirect())
    )

    val pool = P2LThreadPool(3, 3)
    val discoveredOtherIdentities = P2LFuture.oneForAll(pool.execute(nodes.indices.map {i ->
        P2LThreadPool.ProvidingTask {
            println("knownIdentities[$i] = ${knownIdentities[i].first}")
            println("$i - others = ${knownIdentities.filterNot { it == knownIdentities[i] }.map { it.first }}")
            Pair(i,
                RendezvousServer.rendezvousWith(nodes[i], rendezvousLink,
                        IdentityTriple(knownIdentities[i].first, decodeKeyPair(knownIdentities[i].second).public.encoded, nodes[i].selfLink),
                        10000,
                        *knownIdentities.filterNot { it == knownIdentities[i] }.map { it.first }.toTypedArray()
                )
            )
        }
    }))

    //note: nodes or identities don't currently
    val eachFoundIdentities = discoveredOtherIdentities.get(2000)
            .sortedBy { it.first }.map { it.second } //required to resort the rendezvous results after 'oneForAll' has possibly scrambled the order

    val instances = eachFoundIdentities.withIndex().map {(i, foundIdentities) ->
        startInstance(nodes[i], knownIdentities[i].first, decodeKeyPair(knownIdentities[i].second), foundIdentities.map { Pair(it.name, base64Encode(it.publicKey)) })
    }
    for(i in instances.indices)
        instances[i].connect(eachFoundIdentities[i].withIndex().filterNot { it.index == i }.map { it.value.link })

    relayServer.close()
}



fun startConnectedNodesWithIdentities(identities: List<Pair<String, String>>) {
    val instances = ArrayList<Nockchain>(identities.size)
    for((i, id) in identities.withIndex())
        instances.add(
            startNodeWith(30151 + i, id.first, decodeKeyPair(id.second), identities.filterNot { it == id }.map { Pair(it.first, extractPublicKeyFromEncodedKeyPair(it.second)) })
        )
    for(i in 0 until instances.size-1)
        instances[i].connect(instances[i+1].selfLink)
}
fun startNodeWith(port: Int, initialOwnName: String, initialOwnKeyPair: KeyPair, initialContacts: List<Pair<String, String>>) : Nockchain {
    val app = SharedRandomness(Int.MAX_VALUE, 128, 128)
    val instance = Nockchain(app, P2Link.Local.forTest(port).unsafeAsDirect(), consensus = ManualConsensusAlgorithmCreator())
    VisualizationFrame(instance)
    startAppUI(app, instance, initialOwnName, initialOwnKeyPair, initialContacts, false, allowChoosingContacts = true)
    return instance
}

fun startInstance(p2lNode: P2LNode, initialOwnName: String, initialOwnKeyPair: KeyPair, initialContacts: List<Pair<String, String>>) : Nockchain {
    val app = SharedRandomness(Int.MAX_VALUE, 128, 128)
    val instance = Nockchain(app, p2lNode, consensus = ManualConsensusAlgorithmCreator())
    VisualizationFrame(instance)
    startAppUI(app, instance, initialOwnName, initialOwnKeyPair, initialContacts, allowEditingData = false, allowChoosingContacts = true)
    return instance
}

fun startLocalNodeWithSingleUI(knownIdentities: List<Pair<String, String>>) {
    val app = SharedRandomness(Int.MAX_VALUE, 128, 128)
    val instance = Mockchain(app)
    VisualizationFrame(instance) //remove this line to only have the app ui
    //PUB LIST: MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwOqQbaoP3JYga9a67p7z6V7Q8Xd9FrpzB153ILY0LoqKeTHaj60CG4ZmImHBEQcNHkQsdqELjhFGYCwCxUU9A0tczJ8eTPC8uPlbuk1ke0OJ19xG2VXcacjsQDdmEOc3a88EBf1Y/4GTi/D0oUWFaAHWK2opfvEkPvrhpOHdgV/69CLQ3QpYypR+Fr1WcsqZHjRikcfDSD7hSZVv4RluMFx9AL2cW1X2/yx7H41+jsIbi14xhSDMJMTFFfeEVbVTFgy+E90d7rejaLRoSEPb32x1HRbb8/iEryM5C9LoljGmLkJd99XKkhD55xG9Eopx5qLnPwqCAJvWzLMAHnjHBwIDAQAB, MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApUcr6NOugg720mTiPbbbScSo140kz/hLIDFTt3HogfvW5ArsV0xvoM3SfGsX+Bw2F5Hzk415xkV06kBA8zNhNWO5q9BB+uwwlcQVXYTZvNuobC4aud778n0YROWExY+zL+xXMcNTdqywUkSoVo4/CP9MgVwd+Co9u2gZXXxPdhV+/+KhGg2qox8LWLR8uJMPTlfIqNj7vGQdBZpvkyArON2ESd0A1//I07oRB1RCPc66Wm/gmxh2oA8eE4N+9NDSU2oMyun30MNI/BVFtF+W1IT5h6IR38iYKVn5u74IC8exEeFv35eiLMHgi/SGiF8Hli/AlhhFQRCDsQB51uCsQwIDAQAB, MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAh+wh7AozvvYY0PrZXoTVK9fEE7YcIWrXKQtnrEZzoXeeAhS0AWue6uEmQsE2IbNV/J2I0O4bV+KXMk9URm9eO4XW0PexHHc9vS1avBu+p2VjHbGhJSAJz33uHwtMSi/lfe7ZnX/UqbV5XMVEXrPVzQrwvPUHpM647wnfdRq2/hzUUek/s9vNAu43KqDWBgpFmiBamW4sW4gn9kDjo3PzcyHnAMk1k6Xf3fEMDSpyp2EUpxTvgtx9gmnnR9AN/Cjm9g/p5AsVHZ1YHFCB6V4rm+GLMft6hTmiWG2gep94fxew29OnREOoCcQmc/eUPkgyuhMcoO4s/atNePfJ4pveAQIDAQAB
    startAppUI(app, instance, knownIdentities[0].first,
            decodeKeyPair(knownIdentities[0].second),
            knownIdentities.map { Pair(it.first, extractPublicKeyFromEncodedKeyPair(it.second)) },
            allowEditingData =true, allowChoosingContacts = true
    )
}
fun startLocalNodeWithUIForEachIdentity(knownIdentities: List<Pair<String, String>>) {
    val app = SharedRandomness(Int.MAX_VALUE, 128, 128, false)
    val instance = Mockchain(app)
    VisualizationFrame(instance) //remove this line to only have the app ui
    //PUB LIST: MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwOqQbaoP3JYga9a67p7z6V7Q8Xd9FrpzB153ILY0LoqKeTHaj60CG4ZmImHBEQcNHkQsdqELjhFGYCwCxUU9A0tczJ8eTPC8uPlbuk1ke0OJ19xG2VXcacjsQDdmEOc3a88EBf1Y/4GTi/D0oUWFaAHWK2opfvEkPvrhpOHdgV/69CLQ3QpYypR+Fr1WcsqZHjRikcfDSD7hSZVv4RluMFx9AL2cW1X2/yx7H41+jsIbi14xhSDMJMTFFfeEVbVTFgy+E90d7rejaLRoSEPb32x1HRbb8/iEryM5C9LoljGmLkJd99XKkhD55xG9Eopx5qLnPwqCAJvWzLMAHnjHBwIDAQAB, MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApUcr6NOugg720mTiPbbbScSo140kz/hLIDFTt3HogfvW5ArsV0xvoM3SfGsX+Bw2F5Hzk415xkV06kBA8zNhNWO5q9BB+uwwlcQVXYTZvNuobC4aud778n0YROWExY+zL+xXMcNTdqywUkSoVo4/CP9MgVwd+Co9u2gZXXxPdhV+/+KhGg2qox8LWLR8uJMPTlfIqNj7vGQdBZpvkyArON2ESd0A1//I07oRB1RCPc66Wm/gmxh2oA8eE4N+9NDSU2oMyun30MNI/BVFtF+W1IT5h6IR38iYKVn5u74IC8exEeFv35eiLMHgi/SGiF8Hli/AlhhFQRCDsQB51uCsQwIDAQAB, MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAh+wh7AozvvYY0PrZXoTVK9fEE7YcIWrXKQtnrEZzoXeeAhS0AWue6uEmQsE2IbNV/J2I0O4bV+KXMk9URm9eO4XW0PexHHc9vS1avBu+p2VjHbGhJSAJz33uHwtMSi/lfe7ZnX/UqbV5XMVEXrPVzQrwvPUHpM647wnfdRq2/hzUUek/s9vNAu43KqDWBgpFmiBamW4sW4gn9kDjo3PzcyHnAMk1k6Xf3fEMDSpyp2EUpxTvgtx9gmnnR9AN/Cjm9g/p5AsVHZ1YHFCB6V4rm+GLMft6hTmiWG2gep94fxew29OnREOoCcQmc/eUPkgyuhMcoO4s/atNePfJ4pveAQIDAQAB
    knownIdentities.forEach { id ->
        startAppUI(app, instance, id.first, decodeKeyPair(id.second),
                knownIdentities.filterNot { it == id }.map { Pair(it.first, extractPublicKeyFromEncodedKeyPair(it.second)) }, allowEditingData = false, allowChoosingContacts = true
        )
    }
}

fun startAppUI(app: SharedRandomness, instance: Mockchain, initialOwnName: String, initialOwnKeyPair: KeyPair, initialContacts: List<Pair<String, String>>, allowEditingData: Boolean, allowChoosingContacts: Boolean) {
    //
    //  ui that shows:
    //     a list of open challenges (n/m have already contributed)
    //     a toggle button for: 'auto contribute'
    //        if turned off the user is prompted each time a challenge for 'me' comes in and asks to enter randomness
    //     a button to start a challenge
    //     a list of the last 22 completed challenges
    //        pressing an item in this list shows a panel that shows further information
    //        contributors, interpretations(as dice)



    val frame = JFrame("Shared Randomness UI")
    val pendingChallengesListHolder = DefaultListModel<MutableList<ChallengeContribution>>()
    val pendingChallengesListJL = JList(pendingChallengesListHolder)
    val ownNameJTF = LabeledInputField("Own Name: ", 20, BoxLayout.Y_AXIS);ownNameJTF.text = initialOwnName;ownNameJTF.isEditable = allowEditingData
    val ownKeyPairJTF = LabeledInputField("Own Encoded Key Pair: ", 20, BoxLayout.Y_AXIS);ownKeyPairJTF.text = encodeKeyPair(initialOwnKeyPair);ownKeyPairJTF.isEditable = allowEditingData
    val autoGenerateContributions = JCheckBox("automatically generate contributions", true);autoGenerateContributions.horizontalTextPosition = SwingConstants.LEFT
    val addManualContributionJB = JButton("add manual contribution")

    val contactsListHolder = DefaultListModel<Pair<String, String>>()
    for(e in initialContacts) contactsListHolder.addElement(e)
    val contactsListJL = JList(contactsListHolder)
    val deleteSelectedContactsJB = JButton("delete selected")
    val addContactJB = JButton("add contact")
    val startChallengeWithEnteredData = JButton("start challenge")
    val answerSelectedContributionWithEnteredData = JButton("answer contribution")

    val resultsListHolder = DefaultListModel<MutablePair<SharedRandomnessResult, String>>()
    val resultsListJL = JList(resultsListHolder)
    val deleteResultJB = JButton("delete selected")
    val customDiceHolder = DefaultComboBoxModel<DiceConfiguration>()
    val customDiceJC = JComboBox(customDiceHolder)
    val newCustomDice = JButton("add")
    val removeCustomDice = JButton("remove")
    val randomLocationButton = JButton("location")

    val resultDetailsDisplayPanel = JPanel(BorderLayout())
    val resultDetailInterpretationJL = JLabel("NOTHING SELECTED")

    val challengeResponder:(RandomnessChallenge, String) -> Unit = { challenge, contributorName ->
        val contribution = queryContribution(frame, app, autoGenerateContributions.isSelected, "Randomness challenge received from: $contributorName")
        app.contributeToChallenge(instance, challenge, decodeAndVerifyKeyPair(ownKeyPairJTF.text)!!, ownNameJTF.text, contribution)
    }
    ownKeyPairJTF.addTextChangeListener {
        val decoded = decodeAndVerifyKeyPair(ownKeyPairJTF.text)
        app.clearNewChallengeForMeListeners()
        if(decoded!=null)
            app.addNewChallengeForMeListener(decoded.public.encoded, challengeResponder)
        startChallengeWithEnteredData.isEnabled = (!contactsListJL.isSelectionEmpty || !allowChoosingContacts) && decoded != null
        answerSelectedContributionWithEnteredData.isEnabled = !pendingChallengesListJL.isSelectionEmpty && decoded != null
        pendingChallengesListJL.repaint()
    }

    addManualContributionJB.addActionListener {
        val chosenOwnName = JOptionPane.showInputDialog(frame, "Enter own name:")
        val chosenOwnKeyPair = decodeAndVerifyKeyPair(JOptionPane.showInputDialog(frame, "Enter own key pair:\nFormat: pubK[base64]:privK[base64]"))
        val publicKeys = decodePublicKeys(JOptionPane.showInputDialog(frame, "Enter public keys of parties involved in challenge:\nFormat: pubK0[base64], pubK1[base64], <etc...>"))
        val contribution = queryContribution(frame, app, autoGenerateContributions.isSelected)
                app.contributeToChallenge(instance, RandomnessChallenge(app.maxResultRandomnessLength, publicKeys.toList()), chosenOwnKeyPair!!, chosenOwnName, contribution)
    }
    startChallengeWithEnteredData.addActionListener {
        val ownKeyPair = decodeAndVerifyKeyPair(ownKeyPairJTF.text)!!
        val selectedContacts = (if(allowChoosingContacts) contactsListJL.selectedValuesList else contactsListHolder.elements().toList())
        var contactPublicKeys = selectedContacts.map { base64Decode(it.second) }
        if(contactPublicKeys.find { it.contentEquals(ownKeyPair.public.encoded) } == null) //if list of contacts does not contain own pubK
            contactPublicKeys += ownKeyPair.public.encoded
        if(contactPublicKeys.size <= 1)
            contactPublicKeys = decodePublicKeys(JOptionPane.showInputDialog(frame, "No contacts selected.\nYou can cancel and select some.\nOr you can enter the public keys of the parties involved in the challenge:\nFormat: pubK0[base64], pubK1[base64], <etc...>")).toList()
        val contribution = queryContribution(frame, app, autoGenerateContributions.isSelected, ownNameJTF.text+" is creating challenge for ${selectedContacts.joinToString { it.first }}")
        app.startChallenge(instance, RandomnessChallenge(app.maxResultRandomnessLength, contactPublicKeys), ownKeyPair, ownNameJTF.text, contribution)
    }
    answerSelectedContributionWithEnteredData.addActionListener {
        val selIndex = pendingChallengesListJL.selectedIndex
        if(selIndex >= 0 && selIndex < pendingChallengesListHolder.size()) {
            val selected = pendingChallengesListHolder.get(selIndex)

            val contribution = queryContribution(frame, app, autoGenerateContributions.isSelected)
            app.contributeToChallenge(instance, selected.first().challenge, decodeAndVerifyKeyPair(ownKeyPairJTF.text)!!, ownNameJTF.text, contribution)
        }
    }


    pendingChallengesListJL.selectionMode = ListSelectionModel.SINGLE_SELECTION
    pendingChallengesListJL.cellRenderer = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
            val v = value as List<ChallengeContribution>
            var string = v.joinToString { it.contributorName } + " - (" + v.size + "/" + v.first().challenge.size+")"
            val ownKeyPair = decodeAndVerifyKeyPair(ownKeyPairJTF.text)
            if(ownKeyPair != null && v.map { it.contributorPubK.contentHashCode() }.contains(ownKeyPair.public.encoded.contentHashCode()))
                string += " - Contribution Made"
            return super.getListCellRendererComponent(list, string, index, isSelected, cellHasFocus)
        }
    }
    contactsListJL.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    contactsListJL.cellRenderer = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
            val (contactName, contactEncodedPubKey) = value as Pair<String, String>
            return super.getListCellRendererComponent(list, "$contactName: $contactEncodedPubKey", index, isSelected || !allowChoosingContacts, false)
        }
    }
    contactsListJL.selectionModel = object : DefaultListSelectionModel() {
        var gestureStarted = false
        override fun setSelectionInterval(index0: Int, index1: Int) {
            if (!gestureStarted) {
                if (isSelectedIndex(index0)) super.removeSelectionInterval(index0, index1)
                else                          super.addSelectionInterval(index0, index1)
            }
            gestureStarted = true
        }
        override fun setValueIsAdjusting(isAdjusting: Boolean) {
            if (!isAdjusting) gestureStarted = false
        }
    }

    contactsListJL.addListSelectionListener {
        startChallengeWithEnteredData.isEnabled = (!contactsListJL.isSelectionEmpty || !allowChoosingContacts) && decodeAndVerifyKeyPair(ownKeyPairJTF.text) != null
        answerSelectedContributionWithEnteredData.isEnabled = !pendingChallengesListJL.isSelectionEmpty && decodeAndVerifyKeyPair(ownKeyPairJTF.text) != null
    }
    pendingChallengesListJL.addListSelectionListener {
        if(pendingChallengesListJL.selectedValue != null) {
            contactsListJL.clearSelection()
            val expectedContributorsToSelectedPendingChallenge = pendingChallengesListJL.selectedValue.first().challenge.pubKs.map { base64Encode(it) }
            for ((i, contact) in contactsListHolder.elements().asSequence().withIndex()) {
                val contactEncodedPublicKey = contact.second
                if (expectedContributorsToSelectedPendingChallenge.contains(contactEncodedPublicKey))
                    contactsListJL.addSelectionInterval(i, i)
            }
        }
        answerSelectedContributionWithEnteredData.isEnabled = !pendingChallengesListJL.isSelectionEmpty && decodeAndVerifyKeyPair(ownKeyPairJTF.text) != null
    }
    startChallengeWithEnteredData.isEnabled = (!contactsListJL.isSelectionEmpty || !allowChoosingContacts) && decodeAndVerifyKeyPair(ownKeyPairJTF.text) != null
    answerSelectedContributionWithEnteredData.isEnabled = false

    addContactJB.addActionListener {
        contactsListHolder.addElement(Pair(
                JOptionPane.showInputDialog(frame, "Enter contact name:"),
                JOptionPane.showInputDialog(frame, "Enter contact public key:\nFormat: [base64]")
        ))
    }
    deleteSelectedContactsJB.addActionListener {
        if(allowEditingData) {
            val indicesToRemove = contactsListJL.selectedIndices
            indicesToRemove.reverse() //to remove from highest to lowest
            for (itr in indicesToRemove) contactsListHolder.removeElementAt(itr)
        }
    }


    val createTxPanel = JPanel()
    createTxPanel.layout = BoxLayout(createTxPanel, BoxLayout.X_AXIS)
    createTxPanel.add(startChallengeWithEnteredData)
    createTxPanel.add(answerSelectedContributionWithEnteredData)
    createTxPanel.add(addManualContributionJB)

    val contributionTopPanel = JPanel()
    contributionTopPanel.layout = BoxLayout(contributionTopPanel, BoxLayout.Y_AXIS)
    contributionTopPanel.add(autoGenerateContributions)
    contributionTopPanel.add(ownNameJTF)
    contributionTopPanel.add(ownKeyPairJTF)
    val addDelContactPanel = JPanel()
    addDelContactPanel.layout = BoxLayout(addDelContactPanel, BoxLayout.X_AXIS)
    addDelContactPanel.add(addContactJB)
    addDelContactPanel.add(deleteSelectedContactsJB)
    contributionTopPanel.add(addDelContactPanel)
    contributionTopPanel.add(JScrollPane(contactsListJL))
    contributionTopPanel.add(createTxPanel)

    val commonDicePanel = JPanel(GridLayout(3, 4))
    val furtherInterpretationButtonsPanel = JPanel(GridLayout(1, 4))

    resultsListJL.selectionMode = ListSelectionModel.SINGLE_SELECTION
    resultsListJL.cellRenderer = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
            val v = value as MutablePair<SharedRandomnessResult, String>
            val string = "${v.first.by.joinToString { it.first }}: ${v.second}"
            return super.getListCellRendererComponent(list, string, index, isSelected, cellHasFocus)
        }
    }
    resultsListJL.addListSelectionListener {
        val selIndex = resultsListJL.selectedIndex
        if(selIndex >= 0 && selIndex < resultsListHolder.size()) {
            val selected = resultsListHolder.get(selIndex)
            resultDetailInterpretationJL.text = "${selected.r} (from: ${selected.l.by.map { it.first }})"
            for(c in commonDicePanel.components) c.isEnabled = true
            for(c in furtherInterpretationButtonsPanel.components) c.isEnabled = true
            deleteResultJB.isEnabled = true
            customDiceJC.isEnabled = true
        } else {
            resultDetailInterpretationJL.text = "NOTHING SELECTED"
            for(c in commonDicePanel.components) c.isEnabled = false
            for(c in furtherInterpretationButtonsPanel.components) c.isEnabled = false
            deleteResultJB.isEnabled = false
            customDiceJC.isEnabled = false
        }
    }

    deleteResultJB.isEnabled = false
    deleteResultJB.addActionListener {
        resultsListHolder.remove(resultsListJL.selectedIndex)
    }


    val commonDiceButtonListener = { e: ActionEvent ->
        val selected = resultsListJL.selectedValue
        if(selected != null) {
            val db = e.source as JButton
            val ds = when (db.text) {
                "D2" -> listOf(Dice.D2)
                "D3" -> listOf(Dice.D3)
                "D4" -> listOf(Dice.D4)
                "D6" -> listOf(Dice.D6)
                "D8" -> listOf(Dice.D8)
                "D10" -> listOf(Dice.D10)
                "D12" -> listOf(Dice.D12)
                "D16" -> listOf(Dice.D16)
                "D20" -> listOf(Dice.D20)
                "D24" -> listOf(Dice.D24)
                "D30" -> listOf(Dice.D30)
                "D100" -> listOf(Dice.D100)
                else -> throw IllegalStateException("illegal value found")
            }
            selected.r = DiceConfiguration(ds).getRandomResults(selected.first.bytes).toString()
            resultDetailInterpretationJL.text = "${selected.r} (from: ${selected.l.by.map { it.first }})"
            resultsListJL.repaint()
        }
    }
    val bD2 = JButton("D2");commonDicePanel.add(bD2);bD2.addActionListener(commonDiceButtonListener)
    val bD3 = JButton("D3");commonDicePanel.add(bD3);bD3.addActionListener(commonDiceButtonListener)
    val bD4 = JButton("D4");commonDicePanel.add(bD4);bD4.addActionListener(commonDiceButtonListener)
    val bD6 = JButton("D6");commonDicePanel.add(bD6);bD6.addActionListener(commonDiceButtonListener)
    val bD8 = JButton("D8");commonDicePanel.add(bD8);bD8.addActionListener(commonDiceButtonListener)
    val bD10 = JButton("D10");commonDicePanel.add(bD10);bD10.addActionListener(commonDiceButtonListener)
    val bD12 = JButton("D12");commonDicePanel.add(bD12);bD12.addActionListener(commonDiceButtonListener)
    val bD16 = JButton("D16");commonDicePanel.add(bD16);bD16.addActionListener(commonDiceButtonListener)
    val bD20 = JButton("D20");commonDicePanel.add(bD20);bD20.addActionListener(commonDiceButtonListener)
    val bD24 = JButton("D24");commonDicePanel.add(bD24);bD24.addActionListener(commonDiceButtonListener)
    val bD30 = JButton("D30");commonDicePanel.add(bD30);bD30.addActionListener(commonDiceButtonListener)
    val bD100 = JButton("D100");commonDicePanel.add(bD100);bD100.addActionListener(commonDiceButtonListener)
    for(c in commonDicePanel.components) c.isEnabled = false

    val customDicePanel = object: JPanel(BorderLayout()) {
        override fun getMaximumSize() = Dimension(super.getMaximumSize().width, super.getPreferredSize().height)
    }
    customDiceJC.isEnabled = false
    newCustomDice.addActionListener { customDiceHolder.addElement(DiceConfiguration.swingSelector(frame)) }
    removeCustomDice.addActionListener { customDiceHolder.removeElementAt(customDiceJC.selectedIndex) }
    customDiceJC.addActionListener {
        val selected = resultsListJL.selectedValue
        val clickedCustomDiceConfig = customDiceJC.selectedItem
        if(selected != null && clickedCustomDiceConfig is DiceConfiguration) {
            selected.r = clickedCustomDiceConfig.getRandomResults(selected.first.bytes).toString()
            resultDetailInterpretationJL.text = "${selected.r} (from: ${selected.l.by.map { it.first }})"
            resultsListJL.repaint()
        }
    }
    customDicePanel.add(customDiceJC, BorderLayout.CENTER)
    val customDiceFooterPanel = JPanel(GridLayout(2, 1))
    customDiceFooterPanel.add(newCustomDice)
    customDiceFooterPanel.add(removeCustomDice)
    customDicePanel.add(customDiceFooterPanel, BorderLayout.EAST)

    randomLocationButton.addActionListener {
        val selected = resultsListJL.selectedValue
        if(selected != null) {
            val diceResults = DiceConfiguration(Dice(-90, 90), Dice(0, 180), Dice(1, 100_000), Dice(1, 100_000)).getRandomResults(selected.first.bytes)
            val latitudeBase = diceResults[0]
            val longitudeBase = diceResults[1]
            val latitudeDecimal = diceResults[2]
            val longitudeDecimal = diceResults[3]
            val url = "https://www.google.com/maps/@$latitudeBase.$latitudeDecimal,$longitudeBase.$longitudeDecimal,13z"
            Desktop.getDesktop().browse(URI.create(url))
        }
    }
    furtherInterpretationButtonsPanel.add(randomLocationButton)
    for(c in furtherInterpretationButtonsPanel.components) c.isEnabled = false

    resultDetailsDisplayPanel.add(resultDetailInterpretationJL, BorderLayout.NORTH)
    val interpretationPanel = JPanel()
    interpretationPanel.layout = BoxLayout(interpretationPanel, BoxLayout.Y_AXIS)
    interpretationPanel.add(commonDicePanel)
    interpretationPanel.add(customDicePanel)
    interpretationPanel.add(furtherInterpretationButtonsPanel)
    resultDetailsDisplayPanel.add(interpretationPanel, BorderLayout.CENTER)

    val resultsListPanel = JPanel(BorderLayout())
    resultsListPanel.add(JScrollPane(resultsListJL), BorderLayout.CENTER)
    resultsListPanel.add(deleteResultJB, BorderLayout.SOUTH)

    val contributionTopSplitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, pendingChallengesListJL, contributionTopPanel)
    val completedChallengesSplitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, resultsListPanel, resultDetailsDisplayPanel)
    val mainSplitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, contributionTopSplitPane, completedChallengesSplitPane)
    contributionTopSplitPane.resizeWeight = 0.5
    completedChallengesSplitPane.resizeWeight = 0.5
    mainSplitPane.resizeWeight = 0.5
    frame.contentPane.add(mainSplitPane)



    app.addNewChallengeForMeListener(initialOwnKeyPair.public.encoded, challengeResponder)
    app.addNewContributionListener {newContribution ->
        val indexOf = pendingChallengesListHolder.elements().asSequence().indexOfFirst { newContribution.challenge == it.first().challenge }
        if(indexOf == -1)
            pendingChallengesListHolder.addElement(mutableListOf(newContribution))
        else
            pendingChallengesListHolder.get(indexOf).add(newContribution)
        pendingChallengesListJL.repaint()
        resultsListJL.repaint()
    }
    app.addChallengeCompletedListener { challenge, result ->
        resultsListHolder.addElement(MutablePair(result, "NOT INTERPRETED"))
        val indexOf = pendingChallengesListHolder.elements().asSequence().indexOfFirst { challenge == it.first().challenge }
        pendingChallengesListHolder.remove(indexOf)
        pendingChallengesListJL.repaint()
        resultsListJL.repaint()
    }
 

    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.isVisible = true
    frame.setSize(800, 500)
    frame.setLocationRelativeTo(null)
}

fun queryContribution(frame: JFrame, app: SharedRandomness, autoGenerate: Boolean, s: String = ""): ByteArray {
    return if(autoGenerate)
        app.generateContribution()
    else
        JOptionPane.showInputDialog(frame, s + (if(s.isEmpty()) "" else "\n") + "Enter randomness:").toByteArray(Charsets.UTF_8)
}
