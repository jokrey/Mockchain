import jokrey.mockchain.Mockchain
import jokrey.mockchain.Nockchain
import jokrey.mockchain.application.examples.sharedrandomness.RandomnessChallenge
import jokrey.mockchain.application.examples.sharedrandomness.SharedRandomness
import jokrey.mockchain.consensus.ManualConsensusAlgorithm
import jokrey.mockchain.consensus.ManualConsensusAlgorithmCreator
import jokrey.utilities.base64Decode
import jokrey.utilities.decodeKeyPair
import jokrey.utilities.extractPublicKeyFromEncodedKeyPair
import jokrey.utilities.misc.RSAAuthHelper
import jokrey.utilities.network.link2peer.P2Link
import jokrey.utilities.network.link2peer.node.core.NodeCreator
import jokrey.utilities.network.link2peer.rendezvous.IdentityTriple
import jokrey.utilities.network.link2peer.rendezvous.RendezvousServer
import jokrey.utilities.network.link2peer.util.P2LFuture
import jokrey.utilities.network.link2peer.util.P2LThreadPool
import jokrey.utilities.simple.data_structure.stack.ConcurrentStackTest.sleep
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 *
 * @author jokrey
 */
class SharedRandomnessTest {
    @Test
    fun singleNodeTest() {
        val app = SharedRandomness()
        val instance = Mockchain(app)
        val consensus = instance.consensus as ManualConsensusAlgorithm

        val kp1 = RSAAuthHelper.generateKeyPair()
        val kp2 = RSAAuthHelper.generateKeyPair()
        val kp3 = RSAAuthHelper.generateKeyPair()

        val challenge = RandomnessChallenge(67, listOf(kp1.public.encoded, kp2.public.encoded, kp3.public.encoded))
        val future = app.startChallenge(instance, challenge, kp1, "1", app.generateContribution())

        app.addNewChallengeForMeListener(kp2.public.encoded) { newChallenge, _ ->
            app.contributeToChallenge(instance, newChallenge, kp2, "2", app.generateContribution())
        }
        app.addNewChallengeForMeListener(kp3.public.encoded) { newChallenge, _ ->
            app.contributeToChallenge(instance, newChallenge, kp3, "3", app.generateContribution())
        }
        consensus.performConsensusRound(0)
        consensus.performConsensusRound(0)

        assertEquals(67, future.get(1000).size)
    }


    @Test
    fun relayServerManualOpenSharedRandomnessInstances() {
        val knownIdentities = listOf(
                Pair("Peter", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwOqQbaoP3JYga9a67p7z6V7Q8Xd9FrpzB153ILY0LoqKeTHaj60CG4ZmImHBEQcNHkQsdqELjhFGYCwCxUU9A0tczJ8eTPC8uPlbuk1ke0OJ19xG2VXcacjsQDdmEOc3a88EBf1Y/4GTi/D0oUWFaAHWK2opfvEkPvrhpOHdgV/69CLQ3QpYypR+Fr1WcsqZHjRikcfDSD7hSZVv4RluMFx9AL2cW1X2/yx7H41+jsIbi14xhSDMJMTFFfeEVbVTFgy+E90d7rejaLRoSEPb32x1HRbb8/iEryM5C9LoljGmLkJd99XKkhD55xG9Eopx5qLnPwqCAJvWzLMAHnjHBwIDAQAB:MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDA6pBtqg/cliBr1rrunvPpXtDxd30WunMHXncgtjQuiop5MdqPrQIbhmYiYcERBw0eRCx2oQuOEUZgLALFRT0DS1zMnx5M8Ly4+Vu6TWR7Q4nX3EbZVdxpyOxAN2YQ5zdrzwQF/Vj/gZOL8PShRYVoAdYrail+8SQ++uGk4d2BX/r0ItDdCljKlH4WvVZyypkeNGKRx8NIPuFJlW/hGW4wXH0AvZxbVfb/LHsfjX6OwhuLXjGFIMwkxMUV94RVtVMWDL4T3R3ut6NotGhIQ9vfbHUdFtvz+ISvIzkL0uiWMaYuQl331cqSEPnnEb0SinHmouc/CoIAm9bMswAeeMcHAgMBAAECggEBAKplBJi4YzY1LAHUMlxd7ZatduQw5D3VBZD2sUYlaUXKfLC7hg7tgzUIquGncj41+jJHiPZnHKupOn3roa7YjyF/yUG7MapH4ImJRqnxfdUaPIB7QeDpY7vUCkhWJkK710nUGfuoYJmdu9MZSxm/LCxHowHJzUkgeSFfuzpFfb6snTy+GRhdoROxcp8RgPKQDVnxrUGylB1OpUOaqfHpO5YBQno7Vt0MlZJR4hMfHuCTdyze/2M+b9v3cSMHH0RQH7MTbSdDvzUZfTq+V7zcXvaUbAQ+EQ5fumXQXzQePO08byKETg8OpWkZJeY5wO74O+TYZ2WCjX4mZuniQlRjt1ECgYEA5xwmYQq2SEvIZFMK+Bq7uHalXxzYlwz/zIm2ayu7igDbP0Zv62WrlIgCkvp/Sonj0Oc1R8z62XaX3kbOCK5jeHKBOzqOmbiLwiZ/XWA1CmIJMV8DD/tDael0nvsitjGXjjvmm3O++2OqZfCICPjfKRNLmwpPhOBsf7hUbK9TFG0CgYEA1bFjuqXjVnZG+T08JYYJ6DVJMeDBVIMOLLfgKVC0rjC9SZmWNNGZJJ4Q+JnNxsRsHyzyT9MZgc7TRUfA0/ke6C8dFukeFpDLPQGoktAfdyAX/cRZGaeqlaGAsSF19z15f4jpQlZkknz6GU3Ce6zMzZe3OqqyLv/ZIeJ4QsGeGMMCgYAkyZZSXCIn3+hGD/HvDFJVSo2IVk8jvC37oPAonw17Kie8Krol/kkRm5TNUJJyiwB4gFU62KYVd4s1FpA1UY0D3zYy9187mOSmQvqDIo1O2cwcz8LtCFHyyfaGV/NujPZS7bYHiKUd3v+Auojs5LChGTEvvLRrsk2TBwRpSH8xAQKBgBWSitbU2FZqKlAO9ntzRJzEhFccsWeus0egaGjDVPogwXsknh1G64bezifKnxNp0OB00SFt1i1ci8d6ruS3SX93AiDF99ufUmUePb5UdFi6TLG5mKUWYAoq+6rmDdqfwhw13hZsUkrXgwf66Z9CmopGvqCVitdjzK+3BRz4HtWxAoGBAIq61bVhmBxwJCKTw2txSLw2bgOP62RITHFxAfweBbxDeAU4rg8hbPuxj9yhlQsYnMEUPfvxh65FwMiRyMj+3S5J/NxaspioLYGjqZfe+Fz05IrVjflJ/b5G7D0eV3duuunBbTOEFQtn86DYZ8GWgvn6cy+2AaiA/YiXYzYpqPuu"),
                Pair("Otto", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApUcr6NOugg720mTiPbbbScSo140kz/hLIDFTt3HogfvW5ArsV0xvoM3SfGsX+Bw2F5Hzk415xkV06kBA8zNhNWO5q9BB+uwwlcQVXYTZvNuobC4aud778n0YROWExY+zL+xXMcNTdqywUkSoVo4/CP9MgVwd+Co9u2gZXXxPdhV+/+KhGg2qox8LWLR8uJMPTlfIqNj7vGQdBZpvkyArON2ESd0A1//I07oRB1RCPc66Wm/gmxh2oA8eE4N+9NDSU2oMyun30MNI/BVFtF+W1IT5h6IR38iYKVn5u74IC8exEeFv35eiLMHgi/SGiF8Hli/AlhhFQRCDsQB51uCsQwIDAQAB:MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQClRyvo066CDvbSZOI9tttJxKjXjSTP+EsgMVO3ceiB+9bkCuxXTG+gzdJ8axf4HDYXkfOTjXnGRXTqQEDzM2E1Y7mr0EH67DCVxBVdhNm826hsLhq53vvyfRhE5YTFj7Mv7Fcxw1N2rLBSRKhWjj8I/0yBXB34Kj27aBldfE92FX7/4qEaDaqjHwtYtHy4kw9OV8io2Pu8ZB0Fmm+TICs43YRJ3QDX/8jTuhEHVEI9zrpab+CbGHagDx4Tg3700NJTagzK6ffQw0j8FUW0X5bUhPmHohHfyJgpWfm7vggLx7ER4W/fl6IsweCL9IaIXweWL8CWGEVBEIOxAHnW4KxDAgMBAAECggEALwS0RSQTPQSsyuXQRuZCNBYyQj/w/QkRtjLSMhnBn1jZpT5GRf+EsiZbfvGoe/jqmoH23T8eKX2Q6SMmVwmC2gFozKwOWSfgGnsR6OzmVIfYvg3PpJj+69kSkmcJAnsC6ts9YvbCQ7yU3JKToSwOWqzmQtbF39eEgE/5B1NZ96loV62EAf9RM5tSKkicEUg83LqylJZmuDQkQYDcokbU9V7JekyTxBIzTv9st/9cvwbZ3U5rR0AdiCmBr+LA+LlX68VxDo6SZ/JsnmNJWEl4uTzFK54KLX4Ng6msKfCpMki5ZZ8NDglNySJgACUNfCFz7t0240BItu3wmpKueO/KQQKBgQDhM5x+xXfD5wGyh2L7QaUtwmP519lPA7GTJfLAepX62iYuUmbgrcWvBqyLsLiW1EeaHSWHDa1dh9DEErPDtzeQcQJT9VESKELaNMQW09tbGzo/Mx5Rtfc0AjwCzPwswzgL5hSrJKW/KYCZe+8ebOyx/rqRVB4vTvwyIJt2a9rkawKBgQC74Z0jRVN0V98YClidELY2uGRNJqqbcIsKwd72gjh5CAzCGMWgg1diOqSs1iPe19IHoaHj8APUmdJ0rVI3+tWcG+RHJFz2Q/rLlrX41ntSXsHFCPNGu7FGQCxpdEQJtWTPGcdZZObZejfCm0sjN2rdI6mc4RpFhT0iKGDvRCkNiQKBgC3WDmUzFfRWoV7P9ZKEQvV0Wlrw1vchHlR/5c/NY5diLWFCPlQ+qjy8lAP+nSN943D3u7qoSv/9c71kvRf5w6JvjfS+upiCf1DgaoTm6/+4I/vXELW63qzEQ6iiRjVqKo8pbk2DMQUekmEq+3lq3CZCXYDU6Svh3KzrPBk3TJ8vAoGBAKttBuiIt8W+63LO9d2RwwAYrIPslNwxCtys2hhH5ukf3Cw5WBDF5jRdV6XP2XjZqOyHoOQOOiCAnZMSFaO4PbErjdUPq7aTfkDGaZD7ehhFFz4FlZtjZDO6GAu8JtxI4wtH9SlutGeYaUoqUZt3VA0kHf1jMopeMNJ6zz9hDKgpAoGAFO7EL/W/z1GCiOoBTgvXAeLpc+GvitwYxyoYPb7r4evjhnLNhVV92x1C+zt9jXprhBHlZxFfU9E+ITu7fGmYvBpxkbabVg6gCB6bs49N162uOrBfnmjHoB+87CSp3QWRvmVJNqXLEhDUNvm6/WoFQ3RBqcDJDU6UmqoZRowkCFM="),
                Pair("Gertrud", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAh+wh7AozvvYY0PrZXoTVK9fEE7YcIWrXKQtnrEZzoXeeAhS0AWue6uEmQsE2IbNV/J2I0O4bV+KXMk9URm9eO4XW0PexHHc9vS1avBu+p2VjHbGhJSAJz33uHwtMSi/lfe7ZnX/UqbV5XMVEXrPVzQrwvPUHpM647wnfdRq2/hzUUek/s9vNAu43KqDWBgpFmiBamW4sW4gn9kDjo3PzcyHnAMk1k6Xf3fEMDSpyp2EUpxTvgtx9gmnnR9AN/Cjm9g/p5AsVHZ1YHFCB6V4rm+GLMft6hTmiWG2gep94fxew29OnREOoCcQmc/eUPkgyuhMcoO4s/atNePfJ4pveAQIDAQAB:MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCH7CHsCjO+9hjQ+tlehNUr18QTthwhatcpC2esRnOhd54CFLQBa57q4SZCwTYhs1X8nYjQ7htX4pcyT1RGb147hdbQ97Ecdz29LVq8G76nZWMdsaElIAnPfe4fC0xKL+V97tmdf9SptXlcxURes9XNCvC89QekzrjvCd91Grb+HNRR6T+z280C7jcqoNYGCkWaIFqZbixbiCf2QOOjc/NzIecAyTWTpd/d8QwNKnKnYRSnFO+C3H2CaedH0A38KOb2D+nkCxUdnVgcUIHpXiub4Ysx+3qFOaJYbaB6n3h/F7Db06dEQ6gJxCZz95Q+SDK6Exyg7iz9q01498nim94BAgMBAAECggEAR5AKmCUS84LMtBKuqXYUaj3yzVH/Y5TF7aVEk06QiL3a4kuWLn1EMXQTWegyIPIz3onuw9npaY8yfdmIjIEMQxiHboRKqqsZRWYAtLOC4M2frr2cE1jX8XfjDFM9en3XPUOpLaRlCmkymaZ/BcF3Wrpc34++04XHlotDLHvBRu6QaMEgeiTprMX/Op06Q1AahVPSUqAOPAim912Se5q1UdqalrrKNpDcpgNqf62uRcMmstd6it/hlIvoDV4n4ypuIWZTBGvdQj4VLlFlc4c5l6VEies4H9Otzv6TuC8lAmUHNLnFj2b6mrjW0jPTREdED7qkvwHiEOh5DfrvhAe3AQKBgQC76s2OnaHzVn+Vc1FXZoU4e/DBacxmqNq1a31cURELLVksRAHfabBGtWX1bIeNeA6ko/Z4E/0bbHoev5lbPZzv9+81pmyXp3XrXyuYNRdc8e5ZTTVG/+PnhUcjzg1CAUKi0TBtpz+zFK5U7jUY9jvSzNRnuAOU8e6Jf3gm5Vb1kQKBgQC5KthnuT7YQkIZ/87FrEVIyZbp3eB1T2MTxNR5p4FuNwU6169ydy93cGAr/n7WloUox8V6RaQQKr5y0sKo6VaiICQ3udUc2utTc4ETe3wivUcvu7fUdUYT9EoDKBRu+FK9Q1IQa6qQDQQtz08aFN/CrTCMG1NFZbiU1F2QX/FpcQKBgQCVCq8MPRP01xcL5tGN+38QBKU4EfyPM797goyECrv03HvMcwf1NXMdMcRzOifs2Vrr1Cuoo1ntRUU6XAZ66kwtu7ybFastQSFylCIUb49fJXdAls75x/zvZLK+wC+duTgrwLSjU7JfC7kVHXU5nhpmoBSbSsR0fsoNfe9DEkS9MQKBgQCJohAyoN3WjwFlI+BEy/S/0p+q+7HgYH7Lbe1k853gF2N6xmDxmyecBtplOQh8ZmtZ0Yu2g9cb8TmYTZJFTROI9I0XIrkGdq6eW+dgXNP7Wmd0Unqkn/rT0CvHRt5RUaDmbwirjeu8oQAvML2iLEvZ/zNroM/3cFGPxn45Vycw8QKBgAi/iQogPwDIt6OZgyE8VqHM29LSDh6WjvQZ78g7d/joeTuNlnM2ZAR07/9f+8sJsJuAtwV+uvS4toSysqe720/2tn6zk+qHm/VnO+dnIAl8AwKUwkzLOJ7/uiA0R2bxru4mny7sHjreAUs9m23a6sRvDxEpZ2hqcjl5KglKAjD1")
        )


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

        //note: each node in 'nodes' and each identity in 'identities' has or requires knowledge of the others for this to work (except for their name)
        //      this makes for great distributed usage
        val eachFoundIdentities = discoveredOtherIdentities.get(2000)
                .sortedBy { it.first }.map { it.second } //required to resort the rendezvous results after 'oneForAll' has possibly scrambled the order

        val instances = eachFoundIdentities.withIndex().map {(i, foundIdentities) ->
            val app = SharedRandomness(4, 128, 128)
            val instance = Nockchain(app, nodes[i], consensus = ManualConsensusAlgorithmCreator())
            Pair(app, instance)
        }
        for(i in 0 until instances.size-1)
            instances[i].second.connect(instances[i+1].second.selfLink)

        relayServer.close()

        //auto answer contribution functionality.
        for(i in instances.indices)
            instances[i].first.addNewChallengeForMeListener(base64Decode(extractPublicKeyFromEncodedKeyPair(knownIdentities[i].second))) { newChallenge, _ ->
                instances[i].first.contributeToChallenge(instances[i].second, newChallenge, decodeKeyPair(knownIdentities[i].second), knownIdentities[i].first, instances[i].first.generateContribution())
            }

        val future = instances[0].first.startChallenge(instances[0].second,
                RandomnessChallenge(123, eachFoundIdentities[0].map { it.publicKey } + base64Decode(extractPublicKeyFromEncodedKeyPair(knownIdentities[0].second))),
                decodeKeyPair(knownIdentities[0].second),
                knownIdentities[0].first,
                instances[0].first.generateContribution()
                )

        (instances[0].second.consensus as ManualConsensusAlgorithm).performConsensusRound(0)
        sleep(500)
        (instances[0].second.consensus as ManualConsensusAlgorithm).performConsensusRound(0)

        assertEquals(123, future.get(1000).size)
    }

    @Test
    fun relayServerUsingStaticSharedRandomness() {
        val knownIdentities = listOf(
                Pair("Peter", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwOqQbaoP3JYga9a67p7z6V7Q8Xd9FrpzB153ILY0LoqKeTHaj60CG4ZmImHBEQcNHkQsdqELjhFGYCwCxUU9A0tczJ8eTPC8uPlbuk1ke0OJ19xG2VXcacjsQDdmEOc3a88EBf1Y/4GTi/D0oUWFaAHWK2opfvEkPvrhpOHdgV/69CLQ3QpYypR+Fr1WcsqZHjRikcfDSD7hSZVv4RluMFx9AL2cW1X2/yx7H41+jsIbi14xhSDMJMTFFfeEVbVTFgy+E90d7rejaLRoSEPb32x1HRbb8/iEryM5C9LoljGmLkJd99XKkhD55xG9Eopx5qLnPwqCAJvWzLMAHnjHBwIDAQAB:MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDA6pBtqg/cliBr1rrunvPpXtDxd30WunMHXncgtjQuiop5MdqPrQIbhmYiYcERBw0eRCx2oQuOEUZgLALFRT0DS1zMnx5M8Ly4+Vu6TWR7Q4nX3EbZVdxpyOxAN2YQ5zdrzwQF/Vj/gZOL8PShRYVoAdYrail+8SQ++uGk4d2BX/r0ItDdCljKlH4WvVZyypkeNGKRx8NIPuFJlW/hGW4wXH0AvZxbVfb/LHsfjX6OwhuLXjGFIMwkxMUV94RVtVMWDL4T3R3ut6NotGhIQ9vfbHUdFtvz+ISvIzkL0uiWMaYuQl331cqSEPnnEb0SinHmouc/CoIAm9bMswAeeMcHAgMBAAECggEBAKplBJi4YzY1LAHUMlxd7ZatduQw5D3VBZD2sUYlaUXKfLC7hg7tgzUIquGncj41+jJHiPZnHKupOn3roa7YjyF/yUG7MapH4ImJRqnxfdUaPIB7QeDpY7vUCkhWJkK710nUGfuoYJmdu9MZSxm/LCxHowHJzUkgeSFfuzpFfb6snTy+GRhdoROxcp8RgPKQDVnxrUGylB1OpUOaqfHpO5YBQno7Vt0MlZJR4hMfHuCTdyze/2M+b9v3cSMHH0RQH7MTbSdDvzUZfTq+V7zcXvaUbAQ+EQ5fumXQXzQePO08byKETg8OpWkZJeY5wO74O+TYZ2WCjX4mZuniQlRjt1ECgYEA5xwmYQq2SEvIZFMK+Bq7uHalXxzYlwz/zIm2ayu7igDbP0Zv62WrlIgCkvp/Sonj0Oc1R8z62XaX3kbOCK5jeHKBOzqOmbiLwiZ/XWA1CmIJMV8DD/tDael0nvsitjGXjjvmm3O++2OqZfCICPjfKRNLmwpPhOBsf7hUbK9TFG0CgYEA1bFjuqXjVnZG+T08JYYJ6DVJMeDBVIMOLLfgKVC0rjC9SZmWNNGZJJ4Q+JnNxsRsHyzyT9MZgc7TRUfA0/ke6C8dFukeFpDLPQGoktAfdyAX/cRZGaeqlaGAsSF19z15f4jpQlZkknz6GU3Ce6zMzZe3OqqyLv/ZIeJ4QsGeGMMCgYAkyZZSXCIn3+hGD/HvDFJVSo2IVk8jvC37oPAonw17Kie8Krol/kkRm5TNUJJyiwB4gFU62KYVd4s1FpA1UY0D3zYy9187mOSmQvqDIo1O2cwcz8LtCFHyyfaGV/NujPZS7bYHiKUd3v+Auojs5LChGTEvvLRrsk2TBwRpSH8xAQKBgBWSitbU2FZqKlAO9ntzRJzEhFccsWeus0egaGjDVPogwXsknh1G64bezifKnxNp0OB00SFt1i1ci8d6ruS3SX93AiDF99ufUmUePb5UdFi6TLG5mKUWYAoq+6rmDdqfwhw13hZsUkrXgwf66Z9CmopGvqCVitdjzK+3BRz4HtWxAoGBAIq61bVhmBxwJCKTw2txSLw2bgOP62RITHFxAfweBbxDeAU4rg8hbPuxj9yhlQsYnMEUPfvxh65FwMiRyMj+3S5J/NxaspioLYGjqZfe+Fz05IrVjflJ/b5G7D0eV3duuunBbTOEFQtn86DYZ8GWgvn6cy+2AaiA/YiXYzYpqPuu"),
                Pair("Otto", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApUcr6NOugg720mTiPbbbScSo140kz/hLIDFTt3HogfvW5ArsV0xvoM3SfGsX+Bw2F5Hzk415xkV06kBA8zNhNWO5q9BB+uwwlcQVXYTZvNuobC4aud778n0YROWExY+zL+xXMcNTdqywUkSoVo4/CP9MgVwd+Co9u2gZXXxPdhV+/+KhGg2qox8LWLR8uJMPTlfIqNj7vGQdBZpvkyArON2ESd0A1//I07oRB1RCPc66Wm/gmxh2oA8eE4N+9NDSU2oMyun30MNI/BVFtF+W1IT5h6IR38iYKVn5u74IC8exEeFv35eiLMHgi/SGiF8Hli/AlhhFQRCDsQB51uCsQwIDAQAB:MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQClRyvo066CDvbSZOI9tttJxKjXjSTP+EsgMVO3ceiB+9bkCuxXTG+gzdJ8axf4HDYXkfOTjXnGRXTqQEDzM2E1Y7mr0EH67DCVxBVdhNm826hsLhq53vvyfRhE5YTFj7Mv7Fcxw1N2rLBSRKhWjj8I/0yBXB34Kj27aBldfE92FX7/4qEaDaqjHwtYtHy4kw9OV8io2Pu8ZB0Fmm+TICs43YRJ3QDX/8jTuhEHVEI9zrpab+CbGHagDx4Tg3700NJTagzK6ffQw0j8FUW0X5bUhPmHohHfyJgpWfm7vggLx7ER4W/fl6IsweCL9IaIXweWL8CWGEVBEIOxAHnW4KxDAgMBAAECggEALwS0RSQTPQSsyuXQRuZCNBYyQj/w/QkRtjLSMhnBn1jZpT5GRf+EsiZbfvGoe/jqmoH23T8eKX2Q6SMmVwmC2gFozKwOWSfgGnsR6OzmVIfYvg3PpJj+69kSkmcJAnsC6ts9YvbCQ7yU3JKToSwOWqzmQtbF39eEgE/5B1NZ96loV62EAf9RM5tSKkicEUg83LqylJZmuDQkQYDcokbU9V7JekyTxBIzTv9st/9cvwbZ3U5rR0AdiCmBr+LA+LlX68VxDo6SZ/JsnmNJWEl4uTzFK54KLX4Ng6msKfCpMki5ZZ8NDglNySJgACUNfCFz7t0240BItu3wmpKueO/KQQKBgQDhM5x+xXfD5wGyh2L7QaUtwmP519lPA7GTJfLAepX62iYuUmbgrcWvBqyLsLiW1EeaHSWHDa1dh9DEErPDtzeQcQJT9VESKELaNMQW09tbGzo/Mx5Rtfc0AjwCzPwswzgL5hSrJKW/KYCZe+8ebOyx/rqRVB4vTvwyIJt2a9rkawKBgQC74Z0jRVN0V98YClidELY2uGRNJqqbcIsKwd72gjh5CAzCGMWgg1diOqSs1iPe19IHoaHj8APUmdJ0rVI3+tWcG+RHJFz2Q/rLlrX41ntSXsHFCPNGu7FGQCxpdEQJtWTPGcdZZObZejfCm0sjN2rdI6mc4RpFhT0iKGDvRCkNiQKBgC3WDmUzFfRWoV7P9ZKEQvV0Wlrw1vchHlR/5c/NY5diLWFCPlQ+qjy8lAP+nSN943D3u7qoSv/9c71kvRf5w6JvjfS+upiCf1DgaoTm6/+4I/vXELW63qzEQ6iiRjVqKo8pbk2DMQUekmEq+3lq3CZCXYDU6Svh3KzrPBk3TJ8vAoGBAKttBuiIt8W+63LO9d2RwwAYrIPslNwxCtys2hhH5ukf3Cw5WBDF5jRdV6XP2XjZqOyHoOQOOiCAnZMSFaO4PbErjdUPq7aTfkDGaZD7ehhFFz4FlZtjZDO6GAu8JtxI4wtH9SlutGeYaUoqUZt3VA0kHf1jMopeMNJ6zz9hDKgpAoGAFO7EL/W/z1GCiOoBTgvXAeLpc+GvitwYxyoYPb7r4evjhnLNhVV92x1C+zt9jXprhBHlZxFfU9E+ITu7fGmYvBpxkbabVg6gCB6bs49N162uOrBfnmjHoB+87CSp3QWRvmVJNqXLEhDUNvm6/WoFQ3RBqcDJDU6UmqoZRowkCFM="),
                Pair("Gertrud", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAh+wh7AozvvYY0PrZXoTVK9fEE7YcIWrXKQtnrEZzoXeeAhS0AWue6uEmQsE2IbNV/J2I0O4bV+KXMk9URm9eO4XW0PexHHc9vS1avBu+p2VjHbGhJSAJz33uHwtMSi/lfe7ZnX/UqbV5XMVEXrPVzQrwvPUHpM647wnfdRq2/hzUUek/s9vNAu43KqDWBgpFmiBamW4sW4gn9kDjo3PzcyHnAMk1k6Xf3fEMDSpyp2EUpxTvgtx9gmnnR9AN/Cjm9g/p5AsVHZ1YHFCB6V4rm+GLMft6hTmiWG2gep94fxew29OnREOoCcQmc/eUPkgyuhMcoO4s/atNePfJ4pveAQIDAQAB:MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCH7CHsCjO+9hjQ+tlehNUr18QTthwhatcpC2esRnOhd54CFLQBa57q4SZCwTYhs1X8nYjQ7htX4pcyT1RGb147hdbQ97Ecdz29LVq8G76nZWMdsaElIAnPfe4fC0xKL+V97tmdf9SptXlcxURes9XNCvC89QekzrjvCd91Grb+HNRR6T+z280C7jcqoNYGCkWaIFqZbixbiCf2QOOjc/NzIecAyTWTpd/d8QwNKnKnYRSnFO+C3H2CaedH0A38KOb2D+nkCxUdnVgcUIHpXiub4Ysx+3qFOaJYbaB6n3h/F7Db06dEQ6gJxCZz95Q+SDK6Exyg7iz9q01498nim94BAgMBAAECggEAR5AKmCUS84LMtBKuqXYUaj3yzVH/Y5TF7aVEk06QiL3a4kuWLn1EMXQTWegyIPIz3onuw9npaY8yfdmIjIEMQxiHboRKqqsZRWYAtLOC4M2frr2cE1jX8XfjDFM9en3XPUOpLaRlCmkymaZ/BcF3Wrpc34++04XHlotDLHvBRu6QaMEgeiTprMX/Op06Q1AahVPSUqAOPAim912Se5q1UdqalrrKNpDcpgNqf62uRcMmstd6it/hlIvoDV4n4ypuIWZTBGvdQj4VLlFlc4c5l6VEies4H9Otzv6TuC8lAmUHNLnFj2b6mrjW0jPTREdED7qkvwHiEOh5DfrvhAe3AQKBgQC76s2OnaHzVn+Vc1FXZoU4e/DBacxmqNq1a31cURELLVksRAHfabBGtWX1bIeNeA6ko/Z4E/0bbHoev5lbPZzv9+81pmyXp3XrXyuYNRdc8e5ZTTVG/+PnhUcjzg1CAUKi0TBtpz+zFK5U7jUY9jvSzNRnuAOU8e6Jf3gm5Vb1kQKBgQC5KthnuT7YQkIZ/87FrEVIyZbp3eB1T2MTxNR5p4FuNwU6169ydy93cGAr/n7WloUox8V6RaQQKr5y0sKo6VaiICQ3udUc2utTc4ETe3wivUcvu7fUdUYT9EoDKBRu+FK9Q1IQa6qQDQQtz08aFN/CrTCMG1NFZbiU1F2QX/FpcQKBgQCVCq8MPRP01xcL5tGN+38QBKU4EfyPM797goyECrv03HvMcwf1NXMdMcRzOifs2Vrr1Cuoo1ntRUU6XAZ66kwtu7ybFastQSFylCIUb49fJXdAls75x/zvZLK+wC+duTgrwLSjU7JfC7kVHXU5nhpmoBSbSsR0fsoNfe9DEkS9MQKBgQCJohAyoN3WjwFlI+BEy/S/0p+q+7HgYH7Lbe1k853gF2N6xmDxmyecBtplOQh8ZmtZ0Yu2g9cb8TmYTZJFTROI9I0XIrkGdq6eW+dgXNP7Wmd0Unqkn/rT0CvHRt5RUaDmbwirjeu8oQAvML2iLEvZ/zNroM/3cFGPxn45Vycw8QKBgAi/iQogPwDIt6OZgyE8VqHM29LSDh6WjvQZ78g7d/joeTuNlnM2ZAR07/9f+8sJsJuAtwV+uvS4toSysqe720/2tn6zk+qHm/VnO+dnIAl8AwKUwkzLOJ7/uiA0R2bxru4mny7sHjreAUs9m23a6sRvDxEpZ2hqcjl5KglKAjD1")
        )


        val rendezvousLink = P2Link.Local.forTest(40000).unsafeAsDirect()
        val relayServer = RendezvousServer(rendezvousLink)

        val nodes = listOf(
                NodeCreator.create(P2Link.Local.forTest(20141).unsafeAsDirect()),
                NodeCreator.create(P2Link.Local.forTest(20142).unsafeAsDirect()),
                NodeCreator.create(P2Link.Local.forTest(20143).unsafeAsDirect())
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

        //note: each node in 'nodes' and each identity in 'identities' has or requires knowledge of the others for this to work (except for their name)
        //      this makes for great distributed usage
        val eachFoundIdentities = discoveredOtherIdentities.get(2000)
                .sortedBy { it.first }.map { it.second } //required to resort the rendezvous results after 'oneForAll' has possibly scrambled the order

        val instances = eachFoundIdentities.withIndex().map {(i, foundIdentities) ->
            val app = SharedRandomness(4, 128, 128)
            val instance = Nockchain(app, nodes[i], consensus = ManualConsensusAlgorithmCreator())
            Pair(app, instance)
        }
        for(i in 0 until instances.size-1)
            instances[i].second.connect(instances[i+1].second.selfLink)

        relayServer.close()

        //auto answer contribution functionality.
        for(i in instances.indices)
            instances[i].first.addNewChallengeForMeListener(base64Decode(extractPublicKeyFromEncodedKeyPair(knownIdentities[i].second))) { newChallenge, _ ->
                instances[i].first.contributeToChallenge(instances[i].second, newChallenge, decodeKeyPair(knownIdentities[i].second), knownIdentities[i].first, instances[i].first.generateContribution())
            }

        val future = instances[0].first.startChallenge(instances[0].second,
                RandomnessChallenge(123, eachFoundIdentities[0].map { it.publicKey } + base64Decode(extractPublicKeyFromEncodedKeyPair(knownIdentities[0].second))),
                decodeKeyPair(knownIdentities[0].second),
                knownIdentities[0].first,
                instances[0].first.generateContribution()
        )

        (instances[0].second.consensus as ManualConsensusAlgorithm).performConsensusRound(0)
        sleep(500)
        (instances[0].second.consensus as ManualConsensusAlgorithm).performConsensusRound(0)

        assertEquals(123, future.get(1000).size)
    }
}