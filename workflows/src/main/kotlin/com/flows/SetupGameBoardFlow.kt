package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.GameStateContract
import com.contractsAndStates.contracts.RobberContract
import com.contractsAndStates.contracts.TurnTrackerContract
import com.contractsAndStates.states.*
import com.contractsAndStates.states.HexTile
import com.r3.corda.lib.tokens.contracts.utilities.of
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker


// *******************
// * Game Start Flow *
// *******************

/**
 * This is the first flow run by a given node in order to propose the start of a new game to other
 * nodes on the network. In it's current implementation the flow requires the specification of which
 * parties will participate in the game. In the future there may be an opportunity to handle this auto-
 * matically through match making functionality.
 */

// TODO: Make this flow testable by separating GameBoard generation functionality from flow logic.

@InitiatingFlow(version = 1)
@StartableByRPC
class SetupGameBoardFlow(val p1: Party, val p2: Party, val p3: Party, val p4: Party) : FlowLogic<SignedTransaction>() {

    companion object {
        object GETTING_NOTARY : ProgressTracker.Step("Getting reference to the notary")
        object INITIALIZING_TRANSACTION : ProgressTracker.Step("Initializing the transaction and transaction builder")
        object ISSUING_COMMANDS : ProgressTracker.Step("Issuing the appropriate commands")
        object CREATING_A_TURN_TRACKER : ProgressTracker.Step("Creating a turn tracker for you buncha' cheaters")
        object SETTING_UP_YOUR_GAMEBOARD : ProgressTracker.Step("Setting up your personal GameBoard on Corda")
        object FINALIZING_GAMEBOARD : ProgressTracker.Step("Finalizing your GameBoard on Corda")
        object ADDING_PORTS_FOR_YOU_SEAFARING_SOULS : ProgressTracker.Step("Adding sea-ports for your sea-faring souls")
        object FINDING_A_VILLAIN_TO_PLAY_THE_ROBBER : ProgressTracker.Step("Finding a villain to play the robber")
        object ADDING_ALL_GAME_STATES_TO_THE_TRANSACTION : ProgressTracker.Step("Adding all states to the transaction")
        object VERIFYING : ProgressTracker.Step("Verifying the transaction")
        object COLLECTING_SIGNATURES : ProgressTracker.Step("Collecting signatures from your fellow citizens of Cordan")
        object FINALIZING_TRANSACTION : ProgressTracker.Step("Finalizing the transaction")
    }

    override val progressTracker = ProgressTracker(
            GETTING_NOTARY,
            INITIALIZING_TRANSACTION,
            ISSUING_COMMANDS,
            CREATING_A_TURN_TRACKER,
            SETTING_UP_YOUR_GAMEBOARD,
            ADDING_PORTS_FOR_YOU_SEAFARING_SOULS,
            FINALIZING_GAMEBOARD,
            ADDING_ALL_GAME_STATES_TO_THE_TRANSACTION,
            FINDING_A_VILLAIN_TO_PLAY_THE_ROBBER,
            VERIFYING,
            COLLECTING_SIGNATURES,
            FINALIZING_TRANSACTION
    )

    @Suspendable
    override fun call(): SignedTransaction {

        /**
         * The following objects define all of the steps required to execute the flow. These steps will
         * be executed in sequence to set up a game board and displayed to the user via a progress tracker.
         */
        // Step 1.  Get a reference to the notary service on the network
        progressTracker.currentStep = GETTING_NOTARY
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Step 2. Create a new transaction builder
        progressTracker.currentStep = INITIALIZING_TRANSACTION
        val tb = TransactionBuilder(notary)

        // Step 3 of  Create a new issue command and add it to the transaction.
        val playerKeys = listOf(p1.owningKey, p2.owningKey, p3.owningKey, p4.owningKey)
        progressTracker.currentStep = ISSUING_COMMANDS
        val issueCommand = Command(GameStateContract.Commands.SetUpGameBoard(), playerKeys)
        val createTurnTracker = Command(TurnTrackerContract.Commands.CreateTurnTracker(), playerKeys)
        tb.addCommand(issueCommand)
        tb.addCommand(createTurnTracker)

        // Step 4. Create a new turn tracker state
        progressTracker.currentStep = CREATING_A_TURN_TRACKER
        val turnTrackerState = TurnTrackerState(participants = listOf(p1, p2, p3, p4))

        // Step 5. Generate data for new game state
        progressTracker.currentStep = SETTING_UP_YOUR_GAMEBOARD

        // Storage for hexTiles that will be randomly generated.
        val hexTiles = arrayListOf<HexTile.Builder>()

        // Available Terrain types which will determine resources generated by hex tiles.
        val terrainTypes: Array<String> = arrayOf("Forest", "Pasture", "Field", "Hill", "Mountain", "Desert")

        /**
         * Ports in Settlers of Cordan enable users to exchange resources at more favourable rates than those available to players generally.
         * To access a port, a player must have previously built a settlement on a hex tile with an adjacent port. The settlement must also
         * be built on one of the designated access point specified below.
         */

        // Available Port Tiles
        progressTracker.currentStep = ADDING_PORTS_FOR_YOU_SEAFARING_SOULS
        progressTracker.nextStep()
        val portTilesTracking = BooleanArray(PortTile.PORT_COUNT)
        val portTiles: ArrayList<PortTile> = arrayListOf(
                PortTile(listOf(2 of Sheep), listOf(1 of Wood, 1 of Brick, 1 of Ore, 1 of Wheat)),
                PortTile(listOf(2 of Wood), listOf(1 of Sheep, 1 of Brick, 1 of Ore, 1 of Wheat)),
                PortTile(listOf(2 of Brick), listOf(1 of Wood, 1 of Sheep, 1 of Ore, 1 of Wheat)),
                PortTile(listOf(2 of Ore), listOf(1 of Wood, 1 of Sheep, 1 of Brick, 1 of Wheat)),
                PortTile(listOf(2 of Wheat), listOf(1 of Wood, 1 of Sheep, 1 of Brick, 1 of Ore)),
                PortTile(listOf(3 of Wheat, 3 of Wood, 3 of Sheep, 3 of Brick, 3 of Ore), listOf(1 of Wheat, 1 of Wood, 1 of Sheep, 1 of Brick, 1 of Ore)),
                PortTile(listOf(3 of Wheat, 3 of Wood, 3 of Sheep, 3 of Brick, 3 of Ore), listOf(1 of Wheat, 1 of Wood, 1 of Sheep, 1 of Brick, 1 of Ore)),
                PortTile(listOf(3 of Wheat, 3 of Wood, 3 of Sheep, 3 of Brick, 3 of Ore), listOf(1 of Wheat, 1 of Wood, 1 of Sheep, 1 of Brick, 1 of Ore)),
                PortTile(listOf(3 of Wheat, 3 of Wood, 3 of Sheep, 3 of Brick, 3 of Ore), listOf(1 of Wheat, 1 of Wood, 1 of Sheep, 1 of Brick, 1 of Ore))
        )

        val portHexTileAccessPointMapping = arrayListOf(
                listOf(AccessPoint(HexTileIndex(0), listOf(TileCornerIndex(5), TileCornerIndex(1)))),
                listOf(AccessPoint(HexTileIndex(1), listOf(TileCornerIndex(0), TileCornerIndex(2))), AccessPoint(HexTileIndex(2), listOf(TileCornerIndex(5)))),
                listOf(AccessPoint(HexTileIndex(2), listOf(TileCornerIndex(2))), AccessPoint(HexTileIndex(6), listOf(TileCornerIndex(0), TileCornerIndex(1)))),
                listOf(AccessPoint(HexTileIndex(11), listOf(TileCornerIndex(1), TileCornerIndex(2)))),
                listOf(AccessPoint(HexTileIndex(15), listOf(TileCornerIndex(2), TileCornerIndex(3))), AccessPoint(HexTileIndex(18), listOf(TileCornerIndex(1)))),
                listOf(AccessPoint(HexTileIndex(18), listOf(TileCornerIndex(4))), AccessPoint(HexTileIndex(17), listOf(TileCornerIndex(2), TileCornerIndex(3)))),
                listOf(AccessPoint(HexTileIndex(16), listOf(TileCornerIndex(3), TileCornerIndex(4)))),
                listOf(AccessPoint(HexTileIndex(12), listOf(TileCornerIndex(4), TileCornerIndex(5))), AccessPoint(HexTileIndex(7), listOf(TileCornerIndex(3)))),
                listOf(AccessPoint(HexTileIndex(3), listOf(TileCornerIndex(4), TileCornerIndex(5))), AccessPoint(HexTileIndex(7), listOf(TileCornerIndex(0))))
        )

        val ports: ArrayList<Port> = arrayListOf()

        for (i in 0 until PortTile.PORT_COUNT) {
            var currPortTileIndex = Math.floor(Math.random() * (portTiles.size)).toInt()
            while (portTilesTracking[currPortTileIndex]) {
                currPortTileIndex = Math.floor(Math.random() * (portTiles.size)).toInt()
            }

            val currPortTile = portTiles[currPortTileIndex]
            ports.add(i, Port(currPortTile, portHexTileAccessPointMapping[i]))
        }

        /**
         * Role trigger tiles are placed on hexTiles to denote the dice role that gives the player the right to harvest a resource on a given turn.
         * These are placed in counter-clockwise order, start from the top left corner of the game board.
         */
        val roleTriggerTilePlacementMapping: Map<Int, Int> = mapOf(
                0 to 5, 1 to 2, 2 to 6, 3 to 3, 4 to 8, 5 to 10, 6 to 9, 7 to 12, 8 to 11, 9 to 4,
                10 to 8, 11 to 10, 12 to 9, 13 to 4, 14 to 5, 15 to 6, 16 to 3, 17 to 11
        )

        /**
         * Order in which rollTriggerTiles are placed on the existing hex tiles (translating counterclockwise placement to row-by-row hexTile ordering)
         */
        val roleTriggerTilePlacementOrder = arrayListOf(0, 11, 10, 1, 12, 17, 9, 2, 13, 18, 16, 8, 3, 14, 15, 7, 4, 5, 6)

        // Array with maximums for a specific type of HexTile that may be added to the game board.
        val checkArray = intArrayOf(4, 4, 4, 3, 3, 1)

        // Array with counters for specific types of HexTiles added to the game board.
        val countArray = intArrayOf(0, 0, 0, 0, 0, 0)

        // Index adjustment variable to account desert placement
        var desertSkippedIndexAdjustment = 0

        for (i in 0 until GameBoardState.TILE_COUNT) {

            // Get a random index between 0 and 5 which will be used to access the HexTileTypes.
            var hexTypeIndex = (Math.random() * 6).toInt()

            // Check to ensure HexTiles selected so far do not exceed max of each type specified in checkArray.
            while (countArray[hexTypeIndex] >= checkArray[hexTypeIndex]) {
                hexTypeIndex = (Math.random() * 6).toInt()
            }

            // Get the hex resource type.
            val hexType = terrainTypes[hexTypeIndex]

            // Get the port (if relevant) to add to the HexTile


            // Create a HexTile to add to the gameboard.
            // Use role trigger placement mapping, role trigger placement order, and desertSkippedIndexAdjustment to ensure that role triggers
            // Are placed in the appropriate order.
            hexTiles.add(i, HexTile.Builder()
                    .with(hexType)
                    .with(if (hexType.equals("Desert")) 0 else roleTriggerTilePlacementMapping.getOrElse(roleTriggerTilePlacementOrder[i - desertSkippedIndexAdjustment]) { 0 })
                    .with(terrainTypes[hexTypeIndex] == "Desert")
                    .with(HexTileIndex(i)))
            countArray[hexTypeIndex]++

            // Establish the index adjustment once a desert HexTile has been encountered.
            if (hexType.equals("Desert")) {
                desertSkippedIndexAdjustment = 1
            }
        }

        /**
         * Define the neighbouring hexTiles for each individual hexTile. In essence, we are creating a fully-connected graph modelling the state of the game board, this is necessary for a number of reasons,
         * including checking for valid placement of new roads and structures without forcing the user to provide unnecessarily specific input.
         */

        /**
         * TODO: Refactor to create a boolean mapping of settlements built with overlapping references such that building a settlement at coordinate 'n' on HexTile 'n'
         * causes the bool variable for the coordinate to flip from false to true, the bool variable is shared by hexTiles.
         */

        // Step 5. Create a new game state
        // Randomize turn order
        val playersList = listOf(p1, p2, p3, p4)
        val randomizedPlayersList = arrayListOf<Party>()
        while (randomizedPlayersList.size < 4) {
            val randomNumber = (Math.random() * 3.99).toInt()
            if (!randomizedPlayersList.contains(playersList[randomNumber])) {
                randomizedPlayersList.add(playersList[randomNumber])
            }
        }

        // Step 6. Create a robber state and issueRobber commands - add both to the transaction
        progressTracker.currentStep = FINDING_A_VILLAIN_TO_PLAY_THE_ROBBER
        val hexTileWithDesert = hexTiles.filter { it.resourceType == "Desert" }.single().build()
        val robberState = RobberState(hexTileWithDesert.hexTileIndex, playersList)
        val createRobberCommand = Command(RobberContract.Commands.CreateRobber(), playerKeys)
        tb.addOutputState(robberState, RobberContract.ID)
        tb.addCommand(createRobberCommand)

        progressTracker.currentStep = FINALIZING_GAMEBOARD
        val newGameState = GameBoardState(PlacedHexTiles.Builder(hexTiles).build(), ports, randomizedPlayersList, turnTrackerState.linearId, robberState.linearId)

        // Step 7. Add the states to the transaction
        progressTracker.currentStep = ADDING_ALL_GAME_STATES_TO_THE_TRANSACTION
        tb.addOutputState(newGameState, GameStateContract.ID)
        tb.addOutputState(turnTrackerState, TurnTrackerContract.ID)

        // Step 8. Verify and sign the transaction
        progressTracker.currentStep = VERIFYING
        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        // Step 8. Create a list of flows with the relevant participants
        progressTracker.currentStep = COLLECTING_SIGNATURES
        val sessions = (newGameState.participants - ourIdentity).map { initiateFlow(it) }.toSet()

        // Step 9. Collect other signatures
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        // Step 10. Run the FinalityFlow
        progressTracker.currentStep = FINALIZING_TRANSACTION

        val linearIDToBePrinted = newGameState.linearId
        val players = newGameState.players
        val playerNames = players.map { it.name.toString() }
        val currPlayer = players[0]

        // TODO: This messaging is not displaying
        System.out.println("\nYour unique game board identified is $linearIDToBePrinted")
        System.out.println("\nYou are playing with $playerNames")

        if (ourIdentity == currPlayer) {
            System.out.println("\nIt is your turn, you should use the BuildInitialSettlementAndRoadFlow to setup the board!")
        } else {
            System.out.println("\nIt is $currPlayer's turn")
        }

        return subFlow(FinalityFlow(stx, sessions))

    }
}

@InitiatedBy(SetupGameBoardFlow::class)
class SetupGameBoardFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                System.out.println("\nSomeone has invited you to play Settlers of Cordan (Catan on Corda)\n")
                val gameBoardState = stx.coreTransaction.outputsOfType<GameBoardState>().single()
                val linearIDToBePrinted = gameBoardState.linearId
                val players = gameBoardState.players
                val playerNames = players.map { it.name.toString() }
                val currPlayer = players[0]
                System.out.println("\nYour unique game board identified is $linearIDToBePrinted")
                System.out.println("\nYou are playing with $playerNames")

                if (ourIdentity == currPlayer) {
                    System.out.println("\nIt is your turn, you should use the BuildInitialSettlementAndRoadFlow to setup the board!")
                } else {
                    System.out.println("\nIt is $currPlayer's turn")
                }

            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }
}