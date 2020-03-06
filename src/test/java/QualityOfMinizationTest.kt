import jokrey.mockchain.Mockchain
import jokrey.mockchain.application.examples.sensornet.SensorNetAnalyzer
import jokrey.mockchain.consensus.ManualConsensusAlgorithm
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import jokrey.mockchain.visualization.VisualizableApp
import java.util.*
import java.util.logging.LogManager

class QualityOfMinizationTest {
    @Test
    fun testPrintQualityForAll() {
        LogManager.getLogManager().reset()

        arrayOf(
//                SingleStringCalculator(),
//                MultiStringCalculator(10),
//                MashedCalculator(100, maxDependencies = 10),
//                Currency(),
//                CurrencyWithHistory(),
                SensorNetAnalyzer()
//                SupplyChain(10, 8)
        ).forEach { testQuality(it, 100, 1000, 50) }
    }

    fun testQuality(source: VisualizableApp, numberOfRuns: Int, numberOfTxToGenerate: Int, blockEvery: Int) {
        val results = ArrayList<Triple<Long, Long, Long>>()
        val resultingTxCounts = ArrayList<Pair<Int, Int>>()
        val resultingTxRejectedAtMemPool = ArrayList<Int>()

        for (i in 1..numberOfRuns) {
            var txRejectedAtMemPool = 0
            var txI = 0
            val random = Random(i.toLong())

            val app = source.newEqualInstance()
            val instance = Mockchain(app)
            instance.consensus as ManualConsensusAlgorithm
            while(instance.chain.persistedTxCount() < numberOfTxToGenerate) {
                val new = app.next(instance, txI.toLong(), random)
                if (new.isPresent) {
                    try {
                        instance.commitToMemPool(new.get())
                    } catch (ex: IllegalArgumentException) {
                        txRejectedAtMemPool++
                    } //hash uniqueness not guaranteed by app.next
                }
                txI++
                if(txI % blockEvery == 0)
                    instance.consensus.performConsensusRound(false)
            }
            instance.consensus.performConsensusRound(false)

            val before = instance.calculateStorageRequirementsInBytes()
            val beforeTxCount = instance.chain.persistedTxCount()

            instance.consensus.performConsensusRound(true)

            val after = instance.calculateStorageRequirementsInBytes()
            val afterTxCount = instance.chain.persistedTxCount()

            val dif = before - after

            assertTrue(before >= after)

            results.add(Triple(before, after, dif))
            resultingTxCounts.add(Pair(beforeTxCount, afterTxCount))
            resultingTxRejectedAtMemPool.add(txRejectedAtMemPool)
        }

        println(source.javaClass.name + "-(" + source.getCurrentParamContentForEqualCreation().toList() + "):\n    " +
                "before([${results.map { it.first }.min()}..${results.map { it.first }.max()}] bytes), " +
                "after([${results.map { it.second }.min()}..${results.map { it.second }.max()}] bytes), " +
                "difference([${results.map { it.third }.min()}..${results.map { it.third }.max()}] bytes), " +
                "average difference(${results.map { it.third }.average()}), " +
                "average minimization in percent(${(results.map { it.third }.average() / results.map { it.first }.average()) * 100})")
        println("    "+
                "before tx count([${resultingTxCounts.map { it.first }.min()}..${resultingTxCounts.map { it.first }.max()}]), " +
                "after tx count([${resultingTxCounts.map { it.second }.min()}..${resultingTxCounts.map { it.second }.max()}])")
        println("    txRejectedAtMemPool([${resultingTxRejectedAtMemPool.min()}..${resultingTxRejectedAtMemPool.max()}])")

        println("before: ");boxPlotPrint(results.map { it.first })
        println("after: ");boxPlotPrint(results.map { it.second })
        println("difference: ");boxPlotPrint(results.map { it.third })
    }

    fun boxPlotPrint(values: List<Long>) {
        val build = StringBuilder()
        for (v in values)
            build.append(v).append(" \\\\ ")
        println(build)
    }
}

//application.examples.calculator.SingleStringCalculator-([]):
//before([167076..167076] bytes), after([1364..1364] bytes), difference([165712..165712] bytes), average difference(165712.0), average minimization in percent(99.183605065958)
//before tx count([1000..1000]), after tx count([1..1])
//txRejectedAtMemPool([0..0])
//before:
//167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\ 167076 \\
//after:
//1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\ 1364 \\
//difference:
//165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\ 165712 \\
//application.examples.calculator.MultiStringCalculator-([10]):
//before([166481..166481] bytes), after([2399..2418] bytes), difference([164063..164082] bytes), average difference(164071.8), average minimization in percent(98.55286789483483)
//before tx count([1000..1000]), after tx count([10..10])
//txRejectedAtMemPool([0..0])
//before:
//166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\ 166481 \\
//after:
//2408 \\ 2409 \\ 2415 \\ 2415 \\ 2413 \\ 2410 \\ 2412 \\ 2405 \\ 2407 \\ 2411 \\ 2412 \\ 2407 \\ 2406 \\ 2407 \\ 2409 \\ 2410 \\ 2405 \\ 2414 \\ 2411 \\ 2408 \\ 2413 \\ 2417 \\ 2411 \\ 2406 \\ 2412 \\ 2412 \\ 2417 \\ 2413 \\ 2407 \\ 2412 \\ 2410 \\ 2408 \\ 2408 \\ 2406 \\ 2410 \\ 2410 \\ 2406 \\ 2403 \\ 2408 \\ 2412 \\ 2404 \\ 2410 \\ 2405 \\ 2409 \\ 2408 \\ 2413 \\ 2403 \\ 2413 \\ 2414 \\ 2411 \\ 2405 \\ 2407 \\ 2407 \\ 2418 \\ 2411 \\ 2402 \\ 2408 \\ 2413 \\ 2409 \\ 2405 \\ 2407 \\ 2404 \\ 2411 \\ 2410 \\ 2411 \\ 2401 \\ 2405 \\ 2410 \\ 2415 \\ 2408 \\ 2410 \\ 2405 \\ 2399 \\ 2407 \\ 2407 \\ 2409 \\ 2411 \\ 2411 \\ 2409 \\ 2412 \\ 2415 \\ 2415 \\ 2410 \\ 2409 \\ 2399 \\ 2408 \\ 2409 \\ 2409 \\ 2413 \\ 2406 \\ 2412 \\ 2412 \\ 2412 \\ 2411 \\ 2409 \\ 2410 \\ 2407 \\ 2407 \\ 2411 \\ 2404 \\
//difference:
//164073 \\ 164072 \\ 164066 \\ 164066 \\ 164068 \\ 164071 \\ 164069 \\ 164076 \\ 164074 \\ 164070 \\ 164069 \\ 164074 \\ 164075 \\ 164074 \\ 164072 \\ 164071 \\ 164076 \\ 164067 \\ 164070 \\ 164073 \\ 164068 \\ 164064 \\ 164070 \\ 164075 \\ 164069 \\ 164069 \\ 164064 \\ 164068 \\ 164074 \\ 164069 \\ 164071 \\ 164073 \\ 164073 \\ 164075 \\ 164071 \\ 164071 \\ 164075 \\ 164078 \\ 164073 \\ 164069 \\ 164077 \\ 164071 \\ 164076 \\ 164072 \\ 164073 \\ 164068 \\ 164078 \\ 164068 \\ 164067 \\ 164070 \\ 164076 \\ 164074 \\ 164074 \\ 164063 \\ 164070 \\ 164079 \\ 164073 \\ 164068 \\ 164072 \\ 164076 \\ 164074 \\ 164077 \\ 164070 \\ 164071 \\ 164070 \\ 164080 \\ 164076 \\ 164071 \\ 164066 \\ 164073 \\ 164071 \\ 164076 \\ 164082 \\ 164074 \\ 164074 \\ 164072 \\ 164070 \\ 164070 \\ 164072 \\ 164069 \\ 164066 \\ 164066 \\ 164071 \\ 164072 \\ 164082 \\ 164073 \\ 164072 \\ 164072 \\ 164068 \\ 164075 \\ 164069 \\ 164069 \\ 164069 \\ 164070 \\ 164072 \\ 164071 \\ 164074 \\ 164074 \\ 164070 \\ 164077 \\
//application.examples.calculator.MashedCalculator-([100, 10]):
//before([300956..338388] bytes), after([12768..16723] bytes), difference([284233..325165] bytes), average difference(307787.2), average minimization in percent(95.48837743639281)
//before tx count([1000..1010]), after tx count([43..62])
//txRejectedAtMemPool([0..0])
//before:
//318235 \\ 326829 \\ 325579 \\ 328203 \\ 326807 \\ 322076 \\ 322047 \\ 326877 \\ 330676 \\ 327422 \\ 323960 \\ 325139 \\ 321489 \\ 331321 \\ 325790 \\ 322764 \\ 323206 \\ 312145 \\ 316060 \\ 320552 \\ 328707 \\ 320981 \\ 309999 \\ 324248 \\ 327345 \\ 323049 \\ 324401 \\ 329851 \\ 332033 \\ 328710 \\ 322632 \\ 320113 \\ 330945 \\ 325872 \\ 324922 \\ 322982 \\ 309291 \\ 327833 \\ 338388 \\ 315335 \\ 318019 \\ 323116 \\ 317558 \\ 309054 \\ 315534 \\ 321701 \\ 324634 \\ 324046 \\ 321007 \\ 328366 \\ 331294 \\ 323867 \\ 328079 \\ 320625 \\ 309215 \\ 314784 \\ 333840 \\ 320526 \\ 328851 \\ 321641 \\ 314372 \\ 320541 \\ 317694 \\ 329632 \\ 317800 \\ 318875 \\ 327887 \\ 315742 \\ 325148 \\ 324309 \\ 307756 \\ 315558 \\ 329946 \\ 323741 \\ 315805 \\ 322538 \\ 330196 \\ 332189 \\ 324191 \\ 330583 \\ 326322 \\ 318006 \\ 320394 \\ 315782 \\ 324079 \\ 327867 \\ 311854 \\ 333193 \\ 315256 \\ 306860 \\ 300956 \\ 321218 \\ 312180 \\ 332355 \\ 318501 \\ 316827 \\ 315011 \\ 322703 \\ 322551 \\ 325960 \\
//after:
//14040 \\ 14152 \\ 14229 \\ 14599 \\ 13353 \\ 14589 \\ 14083 \\ 13374 \\ 15036 \\ 13298 \\ 14568 \\ 14827 \\ 13690 \\ 14861 \\ 14492 \\ 13862 \\ 14345 \\ 15195 \\ 14696 \\ 14583 \\ 13998 \\ 14294 \\ 16118 \\ 15600 \\ 13861 \\ 13357 \\ 13632 \\ 13382 \\ 13610 \\ 13534 \\ 14903 \\ 15312 \\ 15566 \\ 13963 \\ 14278 \\ 15217 \\ 16417 \\ 13541 \\ 13223 \\ 13764 \\ 14279 \\ 14134 \\ 14455 \\ 16679 \\ 15578 \\ 15618 \\ 13867 \\ 15502 \\ 13676 \\ 14656 \\ 13828 \\ 14793 \\ 14609 \\ 13309 \\ 15175 \\ 15426 \\ 13370 \\ 15776 \\ 14858 \\ 14945 \\ 15853 \\ 13410 \\ 14608 \\ 14460 \\ 12768 \\ 15969 \\ 14371 \\ 16156 \\ 14549 \\ 14240 \\ 15185 \\ 15284 \\ 13625 \\ 15797 \\ 13209 \\ 13641 \\ 13733 \\ 14758 \\ 13873 \\ 14373 \\ 14759 \\ 14387 \\ 14199 \\ 15933 \\ 14312 \\ 14381 \\ 15213 \\ 14267 \\ 15246 \\ 16719 \\ 16723 \\ 14019 \\ 15003 \\ 13148 \\ 13956 \\ 15480 \\ 15940 \\ 15116 \\ 14021 \\ 13570 \\
//difference:
//304195 \\ 312677 \\ 311350 \\ 313604 \\ 313454 \\ 307487 \\ 307964 \\ 313503 \\ 315640 \\ 314124 \\ 309392 \\ 310312 \\ 307799 \\ 316460 \\ 311298 \\ 308902 \\ 308861 \\ 296950 \\ 301364 \\ 305969 \\ 314709 \\ 306687 \\ 293881 \\ 308648 \\ 313484 \\ 309692 \\ 310769 \\ 316469 \\ 318423 \\ 315176 \\ 307729 \\ 304801 \\ 315379 \\ 311909 \\ 310644 \\ 307765 \\ 292874 \\ 314292 \\ 325165 \\ 301571 \\ 303740 \\ 308982 \\ 303103 \\ 292375 \\ 299956 \\ 306083 \\ 310767 \\ 308544 \\ 307331 \\ 313710 \\ 317466 \\ 309074 \\ 313470 \\ 307316 \\ 294040 \\ 299358 \\ 320470 \\ 304750 \\ 313993 \\ 306696 \\ 298519 \\ 307131 \\ 303086 \\ 315172 \\ 305032 \\ 302906 \\ 313516 \\ 299586 \\ 310599 \\ 310069 \\ 292571 \\ 300274 \\ 316321 \\ 307944 \\ 302596 \\ 308897 \\ 316463 \\ 317431 \\ 310318 \\ 316210 \\ 311563 \\ 303619 \\ 306195 \\ 299849 \\ 309767 \\ 313486 \\ 296641 \\ 318926 \\ 300010 \\ 290141 \\ 284233 \\ 307199 \\ 297177 \\ 319207 \\ 304545 \\ 301347 \\ 299071 \\ 307587 \\ 308530 \\ 312390 \\
//application.examples.currency.Currency-([]):
//before([296548..306863] bytes), after([2589..2901] bytes), difference([293959..304204] bytes), average difference(298503.58), average minimization in percent(99.10077006516703)
//before tx count([1000..1032]), after tx count([6..7])
//txRejectedAtMemPool([191..377])
//before:
//301209 \\ 298475 \\ 301615 \\ 301430 \\ 302714 \\ 298499 \\ 297244 \\ 299776 \\ 297816 \\ 304167 \\ 301852 \\ 299617 \\ 298827 \\ 298396 \\ 302121 \\ 303554 \\ 301256 \\ 303749 \\ 301378 \\ 302382 \\ 306015 \\ 298877 \\ 302361 \\ 301265 \\ 296548 \\ 306102 \\ 305930 \\ 304419 \\ 297689 \\ 298116 \\ 301122 \\ 302882 \\ 303344 \\ 297056 \\ 306229 \\ 301940 \\ 300413 \\ 297794 \\ 298031 \\ 306719 \\ 300372 \\ 305032 \\ 299815 \\ 301398 \\ 298427 \\ 305591 \\ 303830 \\ 301499 \\ 301991 \\ 301444 \\ 298804 \\ 301307 \\ 302416 \\ 303072 \\ 298764 \\ 300820 \\ 303745 \\ 300608 \\ 302684 \\ 298694 \\ 305366 \\ 303653 \\ 306863 \\ 303197 \\ 303193 \\ 301909 \\ 303304 \\ 303822 \\ 299874 \\ 299955 \\ 298087 \\ 298038 \\ 303647 \\ 305923 \\ 297941 \\ 298223 \\ 299007 \\ 299437 \\ 300492 \\ 301022 \\ 298359 \\ 301847 \\ 299800 \\ 302924 \\ 298864 \\ 297022 \\ 298331 \\ 304776 \\ 297076 \\ 301619 \\ 300561 \\ 300524 \\ 297051 \\ 302682 \\ 297741 \\ 305960 \\ 299480 \\ 299268 \\ 302524 \\ 302613 \\
//after:
//2837 \\ 2773 \\ 2837 \\ 2721 \\ 2658 \\ 2658 \\ 2592 \\ 2837 \\ 2657 \\ 2658 \\ 2720 \\ 2837 \\ 2594 \\ 2657 \\ 2901 \\ 2659 \\ 2653 \\ 2658 \\ 2837 \\ 2723 \\ 2901 \\ 2658 \\ 2901 \\ 2656 \\ 2589 \\ 2901 \\ 2659 \\ 2659 \\ 2653 \\ 2656 \\ 2657 \\ 2719 \\ 2655 \\ 2837 \\ 2658 \\ 2657 \\ 2655 \\ 2591 \\ 2595 \\ 2656 \\ 2837 \\ 2658 \\ 2591 \\ 2656 \\ 2589 \\ 2837 \\ 2658 \\ 2653 \\ 2655 \\ 2837 \\ 2837 \\ 2591 \\ 2653 \\ 2656 \\ 2592 \\ 2656 \\ 2656 \\ 2589 \\ 2659 \\ 2656 \\ 2721 \\ 2901 \\ 2659 \\ 2658 \\ 2657 \\ 2591 \\ 2658 \\ 2901 \\ 2656 \\ 2837 \\ 2594 \\ 2837 \\ 2657 \\ 2722 \\ 2837 \\ 2595 \\ 2656 \\ 2837 \\ 2594 \\ 2658 \\ 2655 \\ 2657 \\ 2593 \\ 2837 \\ 2901 \\ 2837 \\ 2837 \\ 2659 \\ 2837 \\ 2653 \\ 2717 \\ 2653 \\ 2837 \\ 2653 \\ 2653 \\ 2594 \\ 2837 \\ 2773 \\ 2717 \\ 2837 \\
//difference:
//298372 \\ 295702 \\ 298778 \\ 298709 \\ 300056 \\ 295841 \\ 294652 \\ 296939 \\ 295159 \\ 301509 \\ 299132 \\ 296780 \\ 296233 \\ 295739 \\ 299220 \\ 300895 \\ 298603 \\ 301091 \\ 298541 \\ 299659 \\ 303114 \\ 296219 \\ 299460 \\ 298609 \\ 293959 \\ 303201 \\ 303271 \\ 301760 \\ 295036 \\ 295460 \\ 298465 \\ 300163 \\ 300689 \\ 294219 \\ 303571 \\ 299283 \\ 297758 \\ 295203 \\ 295436 \\ 304063 \\ 297535 \\ 302374 \\ 297224 \\ 298742 \\ 295838 \\ 302754 \\ 301172 \\ 298846 \\ 299336 \\ 298607 \\ 295967 \\ 298716 \\ 299763 \\ 300416 \\ 296172 \\ 298164 \\ 301089 \\ 298019 \\ 300025 \\ 296038 \\ 302645 \\ 300752 \\ 304204 \\ 300539 \\ 300536 \\ 299318 \\ 300646 \\ 300921 \\ 297218 \\ 297118 \\ 295493 \\ 295201 \\ 300990 \\ 303201 \\ 295104 \\ 295628 \\ 296351 \\ 296600 \\ 297898 \\ 298364 \\ 295704 \\ 299190 \\ 297207 \\ 300087 \\ 295963 \\ 294185 \\ 295494 \\ 302117 \\ 294239 \\ 298966 \\ 297844 \\ 297871 \\ 294214 \\ 300029 \\ 295088 \\ 303366 \\ 296643 \\ 296495 \\ 299807 \\ 299776 \\
//application.examples.currency.CurrencyWithHistory-([]):
//before([165222..169736] bytes), after([165222..169736] bytes), difference([0..0] bytes), average difference(0.0), average minimization in percent(0.0)
//before tx count([1000..1028]), after tx count([1000..1028])
//txRejectedAtMemPool([386..489])
//before:
//168988 \\ 167547 \\ 165688 \\ 167389 \\ 168486 \\ 165801 \\ 167885 \\ 166532 \\ 167545 \\ 167941 \\ 165669 \\ 168847 \\ 165222 \\ 168243 \\ 167047 \\ 167252 \\ 166493 \\ 169110 \\ 168146 \\ 166184 \\ 169736 \\ 165993 \\ 169356 \\ 168981 \\ 166868 \\ 167332 \\ 165748 \\ 168869 \\ 166568 \\ 167483 \\ 167268 \\ 167087 \\ 166860 \\ 167929 \\ 168050 \\ 165766 \\ 166135 \\ 166179 \\ 165715 \\ 166024 \\ 165789 \\ 165796 \\ 165373 \\ 166035 \\ 169599 \\ 169019 \\ 166181 \\ 166346 \\ 167410 \\ 168159 \\ 167835 \\ 166867 \\ 167015 \\ 166745 \\ 168795 \\ 168629 \\ 166494 \\ 166837 \\ 167707 \\ 168105 \\ 167198 \\ 165680 \\ 166577 \\ 166169 \\ 167876 \\ 165544 \\ 166887 \\ 169406 \\ 166039 \\ 166340 \\ 166055 \\ 167097 \\ 165987 \\ 167490 \\ 169567 \\ 166958 \\ 167218 \\ 167210 \\ 168054 \\ 166583 \\ 166977 \\ 168576 \\ 167131 \\ 165424 \\ 165367 \\ 169000 \\ 166164 \\ 165812 \\ 167186 \\ 165339 \\ 167922 \\ 167192 \\ 166482 \\ 166238 \\ 166653 \\ 168642 \\ 166518 \\ 168530 \\ 167286 \\ 165296 \\
//after:
//168988 \\ 167547 \\ 165688 \\ 167389 \\ 168486 \\ 165801 \\ 167885 \\ 166532 \\ 167545 \\ 167941 \\ 165669 \\ 168847 \\ 165222 \\ 168243 \\ 167047 \\ 167252 \\ 166493 \\ 169110 \\ 168146 \\ 166184 \\ 169736 \\ 165993 \\ 169356 \\ 168981 \\ 166868 \\ 167332 \\ 165748 \\ 168869 \\ 166568 \\ 167483 \\ 167268 \\ 167087 \\ 166860 \\ 167929 \\ 168050 \\ 165766 \\ 166135 \\ 166179 \\ 165715 \\ 166024 \\ 165789 \\ 165796 \\ 165373 \\ 166035 \\ 169599 \\ 169019 \\ 166181 \\ 166346 \\ 167410 \\ 168159 \\ 167835 \\ 166867 \\ 167015 \\ 166745 \\ 168795 \\ 168629 \\ 166494 \\ 166837 \\ 167707 \\ 168105 \\ 167198 \\ 165680 \\ 166577 \\ 166169 \\ 167876 \\ 165544 \\ 166887 \\ 169406 \\ 166039 \\ 166340 \\ 166055 \\ 167097 \\ 165987 \\ 167490 \\ 169567 \\ 166958 \\ 167218 \\ 167210 \\ 168054 \\ 166583 \\ 166977 \\ 168576 \\ 167131 \\ 165424 \\ 165367 \\ 169000 \\ 166164 \\ 165812 \\ 167186 \\ 165339 \\ 167922 \\ 167192 \\ 166482 \\ 166238 \\ 166653 \\ 168642 \\ 166518 \\ 168530 \\ 167286 \\ 165296 \\
//difference:
//0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\ 0 \\
//application.examples.sensornet.SensorNetAnalyzer-([]):
//before([175159..175343] bytes), after([24568..24995] bytes), difference([150236..150685] bytes), average difference(150454.44), average minimization in percent(85.85019426278572)
//before tx count([1040..1040]), after tx count([200..200])
//txRejectedAtMemPool([0..0])
//before:
//175247 \\ 175259 \\ 175219 \\ 175288 \\ 175256 \\ 175308 \\ 175235 \\ 175265 \\ 175276 \\ 175159 \\ 175195 \\ 175249 \\ 175319 \\ 175246 \\ 175312 \\ 175276 \\ 175236 \\ 175276 \\ 175237 \\ 175238 \\ 175290 \\ 175268 \\ 175298 \\ 175230 \\ 175252 \\ 175246 \\ 175198 \\ 175250 \\ 175259 \\ 175208 \\ 175295 \\ 175221 \\ 175206 \\ 175268 \\ 175251 \\ 175257 \\ 175249 \\ 175275 \\ 175255 \\ 175234 \\ 175289 \\ 175257 \\ 175212 \\ 175313 \\ 175240 \\ 175267 \\ 175291 \\ 175300 \\ 175211 \\ 175287 \\ 175212 \\ 175242 \\ 175326 \\ 175288 \\ 175234 \\ 175183 \\ 175279 \\ 175276 \\ 175257 \\ 175243 \\ 175228 \\ 175237 \\ 175237 \\ 175227 \\ 175261 \\ 175291 \\ 175202 \\ 175230 \\ 175216 \\ 175255 \\ 175297 \\ 175343 \\ 175220 \\ 175278 \\ 175282 \\ 175205 \\ 175231 \\ 175259 \\ 175245 \\ 175243 \\ 175332 \\ 175263 \\ 175240 \\ 175242 \\ 175227 \\ 175248 \\ 175305 \\ 175269 \\ 175255 \\ 175186 \\ 175244 \\ 175261 \\ 175253 \\ 175251 \\ 175236 \\ 175252 \\ 175212 \\ 175250 \\ 175169 \\ 175235 \\
//after:
//24661 \\ 24827 \\ 24687 \\ 24850 \\ 24641 \\ 24819 \\ 24783 \\ 24888 \\ 24764 \\ 24774 \\ 24724 \\ 24832 \\ 24770 \\ 24886 \\ 24772 \\ 24722 \\ 24816 \\ 24660 \\ 24911 \\ 24701 \\ 24903 \\ 24814 \\ 24741 \\ 24945 \\ 24745 \\ 24877 \\ 24891 \\ 24780 \\ 24712 \\ 24770 \\ 24635 \\ 24927 \\ 24818 \\ 24812 \\ 24925 \\ 24790 \\ 24757 \\ 24779 \\ 24708 \\ 24850 \\ 24862 \\ 24852 \\ 24688 \\ 24781 \\ 24760 \\ 24950 \\ 24804 \\ 24772 \\ 24583 \\ 24931 \\ 24835 \\ 24895 \\ 24824 \\ 24814 \\ 24698 \\ 24686 \\ 24849 \\ 24979 \\ 24790 \\ 24740 \\ 24769 \\ 24914 \\ 24742 \\ 24772 \\ 24794 \\ 24748 \\ 24811 \\ 24789 \\ 24848 \\ 24708 \\ 24923 \\ 24820 \\ 24814 \\ 24849 \\ 24897 \\ 24742 \\ 24995 \\ 24867 \\ 24876 \\ 24808 \\ 24721 \\ 24854 \\ 24761 \\ 24804 \\ 24646 \\ 24788 \\ 24789 \\ 24799 \\ 24951 \\ 24842 \\ 24777 \\ 24737 \\ 24568 \\ 24917 \\ 24748 \\ 24677 \\ 24772 \\ 24834 \\ 24832 \\ 24723 \\
//difference:
//150586 \\ 150432 \\ 150532 \\ 150438 \\ 150615 \\ 150489 \\ 150452 \\ 150377 \\ 150512 \\ 150385 \\ 150471 \\ 150417 \\ 150549 \\ 150360 \\ 150540 \\ 150554 \\ 150420 \\ 150616 \\ 150326 \\ 150537 \\ 150387 \\ 150454 \\ 150557 \\ 150285 \\ 150507 \\ 150369 \\ 150307 \\ 150470 \\ 150547 \\ 150438 \\ 150660 \\ 150294 \\ 150388 \\ 150456 \\ 150326 \\ 150467 \\ 150492 \\ 150496 \\ 150547 \\ 150384 \\ 150427 \\ 150405 \\ 150524 \\ 150532 \\ 150480 \\ 150317 \\ 150487 \\ 150528 \\ 150628 \\ 150356 \\ 150377 \\ 150347 \\ 150502 \\ 150474 \\ 150536 \\ 150497 \\ 150430 \\ 150297 \\ 150467 \\ 150503 \\ 150459 \\ 150323 \\ 150495 \\ 150455 \\ 150467 \\ 150543 \\ 150391 \\ 150441 \\ 150368 \\ 150547 \\ 150374 \\ 150523 \\ 150406 \\ 150429 \\ 150385 \\ 150463 \\ 150236 \\ 150392 \\ 150369 \\ 150435 \\ 150611 \\ 150409 \\ 150479 \\ 150438 \\ 150581 \\ 150460 \\ 150516 \\ 150470 \\ 150304 \\ 150344 \\ 150467 \\ 150524 \\ 150685 \\ 150334 \\ 150488 \\ 150575 \\ 150440 \\ 150416 \\ 150337 \\ 150512 \\
//application.examples.supplychain.SupplyChain-([10, 8]):
//before([542113..557667] bytes), after([16218..45255] bytes), difference([505086..527404] bytes), average difference(518048.7), average minimization in percent(94.48683397763467)
//before tx count([1000..1017]), after tx count([2..48])
//txRejectedAtMemPool([0..0])
//before:
//549182 \\ 553075 \\ 548437 \\ 542627 \\ 547409 \\ 549351 \\ 547503 \\ 550473 \\ 542346 \\ 542774 \\ 554671 \\ 549070 \\ 549267 \\ 549323 \\ 543547 \\ 548175 \\ 548595 \\ 550331 \\ 551255 \\ 543905 \\ 547055 \\ 548933 \\ 549575 \\ 550891 \\ 544865 \\ 552180 \\ 547109 \\ 549621 \\ 549239 \\ 548185 \\ 549537 \\ 549239 \\ 549883 \\ 549995 \\ 551675 \\ 553047 \\ 542113 \\ 548253 \\ 552011 \\ 549907 \\ 542138 \\ 550051 \\ 549073 \\ 557667 \\ 548101 \\ 544229 \\ 548223 \\ 548745 \\ 548567 \\ 552039 \\ 549267 \\ 548409 \\ 547317 \\ 550527 \\ 550109 \\ 543801 \\ 550387 \\ 544865 \\ 549605 \\ 549809 \\ 548868 \\ 544657 \\ 548437 \\ 551899 \\ 547795 \\ 547923 \\ 548399 \\ 544333 \\ 548325 \\ 548073 \\ 550079 \\ 543502 \\ 543427 \\ 545305 \\ 545629 \\ 549042 \\ 551899 \\ 548595 \\ 548017 \\ 550443 \\ 549193 \\ 548521 \\ 548125 \\ 546565 \\ 550723 \\ 547711 \\ 547500 \\ 548707 \\ 542138 \\ 547487 \\ 543294 \\ 549547 \\ 549305 \\ 548530 \\ 548175 \\ 548487 \\ 546551 \\ 543061 \\ 551927 \\ 549855 \\
//after:
//29670 \\ 44191 \\ 25289 \\ 25594 \\ 32244 \\ 24783 \\ 34367 \\ 34871 \\ 20730 \\ 16218 \\ 45255 \\ 29125 \\ 41109 \\ 26493 \\ 28734 \\ 38227 \\ 29821 \\ 32597 \\ 36485 \\ 21979 \\ 29103 \\ 26753 \\ 40895 \\ 30605 \\ 39779 \\ 32363 \\ 24821 \\ 25400 \\ 37153 \\ 23227 \\ 33797 \\ 32593 \\ 32763 \\ 25251 \\ 28393 \\ 27315 \\ 28201 \\ 32897 \\ 30031 \\ 43094 \\ 16828 \\ 31389 \\ 27585 \\ 42567 \\ 25917 \\ 18899 \\ 33408 \\ 24177 \\ 32843 \\ 30023 \\ 32537 \\ 31117 \\ 24677 \\ 39187 \\ 31933 \\ 32863 \\ 29153 \\ 24883 \\ 27053 \\ 38955 \\ 35144 \\ 33867 \\ 27459 \\ 34721 \\ 21107 \\ 28729 \\ 24405 \\ 28073 \\ 20921 \\ 37950 \\ 31263 \\ 23986 \\ 28117 \\ 25567 \\ 25859 \\ 33216 \\ 36631 \\ 33291 \\ 30182 \\ 28145 \\ 24121 \\ 26303 \\ 36322 \\ 31927 \\ 30885 \\ 25903 \\ 36497 \\ 39813 \\ 16828 \\ 24277 \\ 23912 \\ 34217 \\ 30481 \\ 38228 \\ 30769 \\ 34062 \\ 23788 \\ 28547 \\ 33295 \\ 25719 \\
//difference:
//519512 \\ 508884 \\ 523148 \\ 517033 \\ 515165 \\ 524568 \\ 513136 \\ 515602 \\ 521616 \\ 526556 \\ 509416 \\ 519945 \\ 508158 \\ 522830 \\ 514813 \\ 509948 \\ 518774 \\ 517734 \\ 514770 \\ 521926 \\ 517952 \\ 522180 \\ 508680 \\ 520286 \\ 505086 \\ 519817 \\ 522288 \\ 524221 \\ 512086 \\ 524958 \\ 515740 \\ 516646 \\ 517120 \\ 524744 \\ 523282 \\ 525732 \\ 513912 \\ 515356 \\ 521980 \\ 506813 \\ 525310 \\ 518662 \\ 521488 \\ 515100 \\ 522184 \\ 525330 \\ 514815 \\ 524568 \\ 515724 \\ 522016 \\ 516730 \\ 517292 \\ 522640 \\ 511340 \\ 518176 \\ 510938 \\ 521234 \\ 519982 \\ 522552 \\ 510854 \\ 513724 \\ 510790 \\ 520978 \\ 517178 \\ 526688 \\ 519194 \\ 523994 \\ 516260 \\ 527404 \\ 510123 \\ 518816 \\ 519516 \\ 515310 \\ 519738 \\ 519770 \\ 515826 \\ 515268 \\ 515304 \\ 517835 \\ 522298 \\ 525072 \\ 522218 \\ 511803 \\ 514638 \\ 519838 \\ 521808 \\ 511003 \\ 508894 \\ 525310 \\ 523210 \\ 519382 \\ 515330 \\ 518824 \\ 510302 \\ 517406 \\ 514425 \\ 522763 \\ 514514 \\ 518632 \\ 524136 \\
