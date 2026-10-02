Market listings use Beast Coin from the dedicated server's BEconomy wallet.
The client displays the shared gold_ingot/CMD 6 descriptor and snapshot balance;
checkout validates and debits the actual provider balance on the server thread.
Legacy native-credit listings must be cancelled and relisted by their seller.

SQLite stores the purchase receipt, escrow, currency, price, seller proceeds,
and payment stage. Repeated completed requests never repeat provider calls.
An interrupted or failed provider call has an uncertain outcome: its durable
DEBITING/CREDITING stage blocks repetition and requires reconciliation. This
does not claim an atomic transaction spanning SQLite and BEconomy. An automatic
retry or refund without a provider transaction identifier could duplicate funds.

MarketCheckoutTest uses real SQLite and a provider wallet fake to verify
concurrent requests, restart recovery, escrow, tax, insufficient funds, legacy
price isolation, and uncertain debit/credit handling.
