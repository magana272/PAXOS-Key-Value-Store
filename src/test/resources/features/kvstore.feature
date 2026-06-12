Feature: PAXOS Key-Value Store data semantics
  As a client of the PAXOS-replicated key-value store
  I want PUT/GET/DELETE to behave consistently
  So that consensus-committed writes are observable and idempotent

  Background:
    Given a fresh single-node PAXOS cluster

  Scenario: putting a new key makes it readable
    When I put "alpha" with value "one"
    Then getting "alpha" returns "one"

  Scenario: getting a missing key returns the sentinel
    Then getting "ghost" returns "KEY does not exist"

  Scenario: putting an existing key is rejected
    Given "beta" is already set to "first"
    When I put "beta" with value "second"
    Then the put result is false
    And getting "beta" returns "first"

  Scenario: deleting a key makes it no longer readable
    Given "gamma" is already set to "g"
    When I delete "gamma"
    Then the delete result is true
    And getting "gamma" returns "KEY does not exist"

  Scenario: deleting a missing key reports failure
    When I delete "nonesuch"
    Then the delete result is false

  Scenario Outline: round-trip of multiple keys
    When I put "<key>" with value "<value>"
    Then getting "<key>" returns "<value>"

    Examples:
      | key   | value |
      | k1    | v1    |
      | foo   | bar   |
      | empty |       |
