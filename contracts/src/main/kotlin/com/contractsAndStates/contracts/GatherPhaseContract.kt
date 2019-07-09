package com.contractsAndStates.contracts

import com.contractsAndStates.states.GameBoardState
import com.oracleClient.state.DiceRollState
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

// ***********************
// * Gather Phase Contract *
// ***********************
class GatherPhaseContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.contractsAndStates.contracts.GameStateContract"
    }

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()
        val gameBoardStateReferenced = tx.referenceInputRefsOfType<GameBoardState>().single().state.data
        val diceRollInputState = tx.inputsOfType<DiceRollState>().single()

        when (command.value) {
            is Commands.IssueResourcesToAllPlayers -> requireThat {

                /**
                 *  ******** SHAPE ********
                 */

                "There are no inputs to this transaction" using (tx.inputs.isEmpty())
                "All of the outputs of this transaction are of the FungibleTokenType" using (tx.outputs.all { it.data is FungibleToken })

                /**
                 *  ******** BUSINESS LOGIC ********
                 */

                "The input dice roll is not equal to 7" using (diceRollInputState.randomRoll1 + diceRollInputState.randomRoll2 != 7)

                /**
                 *  ******** SIGNATURES ********
                 */

                val signingParties = tx.commandsOfType<Commands.IssueResourcesToAllPlayers>().single().signers.toSet()
                val participants = gameBoardStateReferenced.participants.map{ it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using(signingParties.containsAll<PublicKey>(participants) && signingParties.size == 4)

            }

        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class IssueResourcesToAllPlayers : Commands
    }
}