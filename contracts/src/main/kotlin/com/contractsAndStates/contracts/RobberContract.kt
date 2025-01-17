package com.contractsAndStates.contracts

import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.RobberState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

// *************************
// * Turn Tracker Contract *
// *************************

class RobberContract : Contract {

    companion object {
        const val ID = "com.contractsAndStates.contracts.RobberContract"
    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()

        val robberInputStates = tx.inputsOfType<RobberState>()
        val robberOutputStates = tx.outputsOfType<RobberState>()

        requireThat {
            "There should be a single output RobberState" using (robberOutputStates.size == 1)
        }

        val outputRobberState = robberOutputStates.single()

        when (command.value) {

            is Commands.CreateRobber -> requireThat {

                /**
                 *  ******** SHAPE ********
                 */
                "There should be no input Robber State" using robberInputStates.isEmpty()
                val outputGameBoards = tx.outputsOfType<GameBoardState>()
                "There should be a single output GameBoardState" using (outputGameBoards.size == 1)
                val outputGameBoard = tx.outputsOfType<GameBoardState>().single()

                /**
                 *  ******** BUSINESS LOGIC ********
                 */
                "The robber state must must belong to the output game board" using
                        outputGameBoard.isValid(outputRobberState)

                /**
                 *  ******** SIGNATURES ********
                 */
                val signingParties = command.signers.toSet()
                val participants = outputGameBoard.participants.map { it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using
                        (signingParties.containsAll(participants) && signingParties.size == 4)
            }

            is Commands.MoveRobber -> requireThat {

                /**
                 *  ******** SHAPE ********
                 */
                val referencedGameBoardStates = tx.referenceInputRefsOfType<GameBoardState>()
                "There should be a single input reference GameBoardState" using (referencedGameBoardStates.size == 1)
                "There must be exactly one Robber input state." using (robberInputStates.size == 1)
                val referencedGameBoardState = referencedGameBoardStates.single().state.data

                /**
                 *  ******** BUSINESS LOGIC ********
                 */
                "The input and output RobberStates should have the same id" using
                        (robberInputStates.single().linearId == outputRobberState.linearId)
                "The robber state must belong to the output game board" using
                        referencedGameBoardState.isValid(outputRobberState)

                /**
                 *  ******** SIGNATURES ********
                 */
                val signingParties = command.signers.toSet()
                val participants = referencedGameBoardState.participants.map { it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using
                        (signingParties.containsAll(participants) && signingParties.size == 4)
            }
        }
    }

    interface Commands : CommandData {
        class CreateRobber : Commands
        class MoveRobber : Commands
    }
}