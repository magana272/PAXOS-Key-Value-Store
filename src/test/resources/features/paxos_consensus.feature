Feature: PAXOS consensus safety with competing clients
  As an operator of the PAXOS-replicated store
  I want the protocol -- not just the key-value map -- to decide the outcome
  So that a value a quorum may already have accepted can never be overwritten
  by a later client, even while it is still uncommitted.

  These scenarios drive a real acceptor quorum through prepare -> accept ->
  commit and use the proposer's real value-selection rule, so they exercise
  Paxos itself rather than KeyValueStore.

  Background:
    Given a PAXOS cluster of 3 acceptors

  Scenario: an accepted-but-uncommitted value is carried forward to a competing client
    When client 1 runs a Paxos round for "x" = "one" at ballot 1
    Then a majority of acceptors accepted "one" for "x"
    And reading "x" returns "KEY does not exist"
    When client 2 runs a Paxos round for "x" = "two" at ballot 2
    Then Paxos makes client 2 choose "one" for "x"
    And a majority of acceptors accepted "one" for "x"
    When the chosen value for "x" is committed
    Then reading "x" returns "one"

  Scenario: a proposal below a promised ballot cannot reach consensus
    When client 1 prepares "y" at ballot 5
    When client 2 runs a Paxos round for "y" = "late" at ballot 2
    Then no majority accepted for "y"
    And reading "y" returns "KEY does not exist"
