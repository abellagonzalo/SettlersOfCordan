package com.contractsAndStates.contracts

import com.contractsAndStates.states.GameBoardState
import com.oracleClientStatesAndContracts.states.DiceRollState
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

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
        val gameBoardStatesReferenced = tx.referenceInputRefsOfType<GameBoardState>()
        val diceRollInputStates = tx.inputsOfType<DiceRollState>()

        requireThat {
            "There should be a single input reference GameBoardState" using (gameBoardStatesReferenced.size == 1)
            "There should be a single input DiceRollState" using (diceRollInputStates.size == 1)
        }

        val gameBoardStateReferenced = gameBoardStatesReferenced.single().state.data
        val diceRollInputState = diceRollInputStates.single()

        when (command.value) {
            is Commands.IssueResourcesToAllPlayers -> requireThat {

                /**
                 *  ******** SHAPE ********
                 */
                "There are no inputs to this transaction" using (tx.inputs.isEmpty())
                "All of the outputs of this transaction are of the FungibleTokenType" using
                        (tx.outputs.all { it.data is FungibleToken })

                /**
                 *  ******** BUSINESS LOGIC ********
                 */
                "The input dice roll is not equal to 7" using !diceRollInputState.isRobberTotal()

                /**
                 *  ******** SIGNATURES ********
                 */
                val signingParties = command.signers.toSet()
                val participants = gameBoardStateReferenced.participants.map { it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using
                        (signingParties.containsAll(participants) && signingParties.size == 4)
            }
        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class IssueResourcesToAllPlayers : Commands
    }
}