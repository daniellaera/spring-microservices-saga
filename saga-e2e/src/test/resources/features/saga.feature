Feature: Order saga across order, inventory and payment
  An order is confirmed only when inventory can reserve every item and the payment succeeds.
  Otherwise it is cancelled, and any stock reserved for it is given back exactly once.

  Scenario: An order paid with an accepted card is confirmed
    Given a product "Keyboard" with 10 in stock
    When the customer orders 2 "Keyboard" paid with an accepted card
    Then the order is CONFIRMED
    And "Keyboard" has 8 in stock
    And one SUCCESS payment transaction exists for the order

  Scenario: An order for more than the stock is cancelled
    Given a product "Monitor" with 1 in stock
    When the customer orders 2 "Monitor" paid with an accepted card
    Then the order is CANCELLED
    And "Monitor" still has 1 in stock
    And one FAILED payment transaction exists for the order
    And no stock is restored for "Monitor"

  Scenario: A declined payment cancels the order and gives the stock back
    Given a product "Headset" with 10 in stock
    When the customer orders 3 "Headset" paid with a declined card
    Then the order is CANCELLED
    And "Headset" has 10 in stock
    And stock is restored exactly once for "Headset"

  Scenario: A duplicate payment failure does not give the stock back twice
    Given a product "Webcam" with 10 in stock
    And the customer orders 3 "Webcam" paid with a declined card
    And the order is CANCELLED
    And "Webcam" has 10 in stock
    When the payment failure of the order is delivered again
    Then "Webcam" still has 10 in stock
    And stock is restored exactly once for "Webcam"

  Scenario: A multi-item order with one item short is cancelled without touching stock
    Given a product "Laptop" with 5 in stock
    And a product "Dock" with 1 in stock
    When the customer orders 2 "Laptop" and 2 "Dock" paid with an accepted card
    Then the order is CANCELLED
    And "Laptop" still has 5 in stock
    And "Dock" still has 1 in stock

  Scenario: A confirmed order leaves its trail on the audit topic
    Given a product "Speaker" with 10 in stock
    When the customer orders 2 "Speaker" paid with an accepted card
    Then the order is CONFIRMED
    And the audit trail of the order shows, in order:
      | event             | service           |
      | ORDER_CREATED     | order-service     |
      | STOCK_DEDUCTED    | inventory-service |
      | PAYMENT_SUCCEEDED | payment-service   |
      | ORDER_CONFIRMED   | order-service     |
