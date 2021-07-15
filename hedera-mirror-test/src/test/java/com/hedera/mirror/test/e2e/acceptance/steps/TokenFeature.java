package com.hedera.mirror.test.e2e.acceptance.steps;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.junit.platform.engine.Cucumber;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import junit.framework.AssertionFailedError;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.CustomFee;
import com.hedera.hashgraph.sdk.CustomFixedFee;
import com.hedera.hashgraph.sdk.CustomFractionalFee;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.client.TopicClient;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorAssessedCustomFee;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorCustomFees;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorFixedFee;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorFraction;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorFractionalFee;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTokenTransfer;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransaction;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTokenResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Log4j2
@Cucumber
public class TokenFeature {
    private static final int INITIAL_SUPPLY = 1_000_000;

    @Autowired
    private TokenClient tokenClient;
    @Autowired
    private AccountClient accountClient;
    @Autowired
    private MirrorNodeClient mirrorClient;
    @Autowired
    private TopicClient topicClient;

    private NetworkTransactionResponse networkTransactionResponse;
    private final List<ExpandedAccountId> recipients = new ArrayList<>();
    private final List<ExpandedAccountId> senders = new ArrayList<>();
    private final Map<TokenId, List<CustomFee>> tokenCustomFees = new HashMap<>();
    private final List<TokenId> tokenIds = new ArrayList<>();

    @Given("I successfully create a new token")
    public void createNewToken() {
        createNewToken(
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE
        );
    }

    @Given("I successfully create a new token with custom fees schedule")
    public void createNewToken(List<CustomFee> customFees) {
        createNewToken(
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE,
                customFees
        );
    }

    @Given("I successfully create a new token with freeze status {int} and kyc status {int}")
    public void createNewToken(int freezeStatus, int kycStatus) {
        createNewToken(RandomStringUtils.randomAlphabetic(4).toUpperCase(), freezeStatus, kycStatus);
    }

    @Given("^I associate a(?:n)? (?:existing|new) sender account(?: (.*))? with token(?: (.*))?$")
    public void associateSenderWithToken(Integer senderIndex, Integer tokenIndex) {
        ExpandedAccountId sender;
        if (senderIndex == null) {
            sender = accountClient.createNewAccount(10_000_000);
            senders.add(sender);
        } else {
            sender = senders.get(senderIndex);
        }

        associateWithToken(sender, tokenIds.get(getIndexOrDefault(tokenIndex)));
    }

    @Given("^I associate a(?:n)? (?:existing|new) recipient account(?: (.*))? with token(?: (.*))?$")
    public void associateRecipientWithToken(Integer recipientIndex, Integer tokenIndex) {
        ExpandedAccountId recipient;
        if (recipientIndex == null) {
            recipient = accountClient.createNewAccount(10_000_000);
            recipients.add(recipient);
        } else {
            recipient = recipients.get(recipientIndex);
        }

        associateWithToken(recipient, tokenIds.get(getIndexOrDefault(tokenIndex)));
    }

    @When("^I set new account (?:(.*) )?freeze status to (.*)$")
    public void setFreezeStatus(Integer recipientIndex, int freezeStatus) {
        setFreezeStatus(freezeStatus, recipients.get(getIndexOrDefault(recipientIndex)));
    }

    @When("^I set new account (?:(.*) )?kyc status to (.*)$")
    public void setKycStatus(Integer recipientIndex, int kycStatus) {
        setKycStatus(kycStatus, recipients.get(getIndexOrDefault(recipientIndex)));
    }

    @Then("^I transfer (.*) tokens (?:(.*) )?to recipient(?: (.*))?$")
    public void transferTokensToRecipient(int amount, Integer tokenIndex, Integer recipientIndex) {
        ExpandedAccountId payer = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        transferTokens(tokenIds.get(getIndexOrDefault(tokenIndex)), amount, payer,
                recipients.get(getIndexOrDefault(recipientIndex)).getAccountId());
    }

    @Then("^I transfer (.*) tokens (?:(.*) )?to sender(?: (.*))?$")
    public void transferTokensToSender(int amount, Integer tokenIndex, Integer senderIndex) {
        ExpandedAccountId payer = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        transferTokens(tokenIds.get(getIndexOrDefault(tokenIndex)), amount, payer,
                senders.get(getIndexOrDefault(senderIndex)).getAccountId());
    }

    @Then("^Sender(?: (.*))? transfers (.*) tokens (?:(.*) )?to recipient(?: (.*))?$")
    public void transferTokensFromSenderToRecipient(Integer senderIndex, int amount, Integer tokenIndex,
                                                    Integer recipientIndex) {
        transferTokens(tokenIds.get(getIndexOrDefault(tokenIndex)), amount, senders.get(getIndexOrDefault(senderIndex)),
                recipients.get(getIndexOrDefault(recipientIndex)).getAccountId());
    }

    @Given("^I update the token(?: (.*))?$")
    public void updateToken(Integer index) {
        networkTransactionResponse = tokenClient.updateToken(tokenIds.get(getIndexOrDefault(index)),
                tokenClient.getSdkClient().getExpandedOperatorAccountId());
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I burn {int} from the token")
    public void burnToken(int amount) {
        networkTransactionResponse = tokenClient.burn(tokenIds.get(0), amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I mint {int} from the token")
    public void mintToken(int amount) {
        networkTransactionResponse = tokenClient.mint(tokenIds.get(0), amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I wipe {int} from the token")
    public void wipeToken(int amount) {
        networkTransactionResponse = tokenClient.wipe(tokenIds.get(0), amount, recipients.get(0));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I dissociate the account from the token")
    public void dissociateNewAccountFromToken() {
        networkTransactionResponse = tokenClient.dissociate(recipients.get(0), tokenIds.get(0));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I delete the token")
    public void deleteToken() {
        networkTransactionResponse = tokenClient
                .delete(tokenClient.getSdkClient().getExpandedOperatorAccountId(), tokenIds.get(0));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        tokenIds.remove(0);
    }

    @Given("I update token {int} with new custom fees schedule")
    public void updateTokenFeeSchedule(int tokenIndex, List<CustomFee> customFees) {
        ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        TokenId tokenId = tokenIds.get(tokenIndex);
        networkTransactionResponse = tokenClient.updateTokenFeeSchedule(tokenId, admin, customFees);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        tokenCustomFees.put(tokenId, customFees);
    }

    @Then("the mirror node REST API should return status {int}")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorAPIResponses(int status) {
        verifyTransactions(status);

        publishBackgroundMessages();
    }

    @Then("^the mirror node REST API should return status (.*) for token (:?(.*) )?fund flow$")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorTokenFundFlow(int status, Integer tokenIndex) {
        verifyMirrorTokenFundFlow(status, tokenIndex, Collections.emptyList());
    }

    @Then("^the mirror node REST API should return status (.*) for token (:?(.*) )?fund flow with assessed custom fees$")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorTokenFundFlow(int status, Integer tokenIndex, List<MirrorAssessedCustomFee> assessedCustomFees) {
        TokenId tokenId = tokenIds.get(getIndexOrDefault(tokenIndex));
        verifyTransactions(status, assessedCustomFees);
        verifyToken(tokenId);
        verifyTokenTransfers(tokenId);

        publishBackgroundMessages();
    }

    @Then("the mirror node REST API should confirm token update")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorTokenUpdateFlow() {
        verifyTokenUpdate(tokenIds.get(0));

        // publish background message to network to reduce possibility of stale info in low TPS environment
        topicClient.publishMessageToDefaultTopic();
    }

    @Then("the mirror node REST API should return status {int} for transaction {string}")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorRestTransactionIsPresent(int status, String transactionIdString) {
        MirrorTransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionIdString);

        List<MirrorTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        MirrorTransaction mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionIdString);

        if (status == HttpStatus.OK.value()) {
            assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        }

        // publish background message to network to reduce possibility of stale info in low TPS environment
        topicClient.publishMessageToDefaultTopic();
    }

    @Then("the mirror node REST API should confirm token {int} with custom fees schedule")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorTokenWithCustomFeesSchedule(Integer tokenIndex) {
        MirrorTransaction transaction = verifyTransactions(200);

        TokenId tokenId = tokenIds.get(getIndexOrDefault(tokenIndex));
        verifyTokenWithCustomFeesSchedule(tokenId, transaction.getConsensusTimestamp());
    }

    @After
    public void cleanup() {
        // dissociate all applicable accounts from token to reduce likelihood of max token association error
        for (TokenId tokenId : tokenIds) {
            // a nonzero balance will result in a TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES error
            // not possible to wipe a treasury account as it results in CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT error
            // as a result to dissociate first delete token
            ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
            try {
                tokenClient.delete(admin, tokenId);

                dissociateAccounts(tokenId, List.of(admin));
                dissociateAccounts(tokenId, recipients);
                dissociateAccounts(tokenId, senders);
            } catch (Exception ex) {
                log.warn("Error cleaning up token {} and associations error: {}", tokenId, ex);
            }
        }

        recipients.clear();
        senders.clear();
        tokenCustomFees.clear();
        tokenIds.clear();
    }

    public AccountId getRecipientAccountId(int index) {
        return recipients.get(index).getAccountId();
    }

    public String getSender(int index) {
        return senders.get(index).getAccountId().toString();
    }

    public TokenId getTokenId(int index) {
        return tokenIds.get(index);
    }

    private void dissociateAccounts(TokenId tokenId, List<ExpandedAccountId> accountIds) {
        for (ExpandedAccountId accountId : accountIds) {
            try {
                tokenClient.dissociate(accountId, tokenId);
                log.info("Successfully dissociated account {} from token {}", accountId, tokenId);
            } catch (Exception ex) {
                log.warn("Error dissociating account {} from token {}, error: {}", accountId, tokenId, ex);
            }
        }
    }

    private void createNewToken(String symbol, int freezeStatus, int kycStatus, List<CustomFee> customFees) {
        ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse = tokenClient.createToken(
                admin,
                symbol,
                freezeStatus,
                kycStatus,
                admin,
                INITIAL_SUPPLY,
                customFees);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        TokenId tokenId = networkTransactionResponse.getReceipt().tokenId;
        assertNotNull(tokenId);
        tokenIds.add(tokenId);
        tokenCustomFees.put(tokenId, customFees);
    }

    private void createNewToken(String symbol, int freezeStatus, int kycStatus) {
        createNewToken(symbol, freezeStatus, kycStatus, Collections.emptyList());
    }

    private void associateWithToken(ExpandedAccountId accountId, TokenId tokenId) {
        networkTransactionResponse = tokenClient.associate(accountId, tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void setFreezeStatus(int freezeStatus, ExpandedAccountId accountId) {
        if (freezeStatus == TokenFreezeStatus.Frozen_VALUE) {
            networkTransactionResponse = tokenClient.freeze(tokenIds.get(0), accountId.getAccountId());
        } else if (freezeStatus == TokenFreezeStatus.Unfrozen_VALUE) {
            networkTransactionResponse = tokenClient.unfreeze(tokenIds.get(0), accountId.getAccountId());
        } else {
            log.warn("Freeze Status must be set to 1 (Frozen) or 2 (Unfrozen)");
        }

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void setKycStatus(int kycStatus, ExpandedAccountId accountId) {
        if (kycStatus == TokenKycStatus.Granted_VALUE) {
            networkTransactionResponse = tokenClient.grantKyc(tokenIds.get(0), accountId.getAccountId());
        } else if (kycStatus == TokenKycStatus.Revoked_VALUE) {
            networkTransactionResponse = tokenClient.revokeKyc(tokenIds.get(0), accountId.getAccountId());
        } else {
            log.warn("Kyc Status must be set to 1 (Granted) or 2 (Revoked)");
        }

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@acceptanceTestProperties.backOffPeriod.toMillis()}"),
            maxAttemptsExpression = "#{@acceptanceTestProperties.maxRetries}")
    private void transferTokens(TokenId tokenId, int amount, ExpandedAccountId sender, AccountId receiver) {
        long startingBalance = tokenClient.getTokenBalance(receiver, tokenId);
        networkTransactionResponse = tokenClient.transferToken(tokenId, sender, receiver, amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        assertThat(tokenClient.getTokenBalance(receiver, tokenId)).isEqualTo(startingBalance + amount);
    }

    private MirrorTransaction verifyTransactions(int status) {
        return verifyTransactions(status, Collections.emptyList());
    }

    private MirrorTransaction verifyTransactions(int status, List<MirrorAssessedCustomFee> assessedCustomFees) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        MirrorTransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        List<MirrorTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        MirrorTransaction mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getAssessedCustomFees()).containsExactlyInAnyOrderElementsOf(assessedCustomFees);

        if (status == HttpStatus.OK.value()) {
            assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        }

        return mirrorTransaction;
    }

    private MirrorTokenResponse verifyToken(TokenId tokenId) {
        MirrorTokenResponse mirrorToken = mirrorClient.getTokenInfo(tokenId.toString());

        assertNotNull(mirrorToken);
        assertThat(mirrorToken.getTokenId()).isEqualTo(tokenId.toString());

        return mirrorToken;
    }

    private void verifyTokenTransfers(TokenId tokenId) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        MirrorTransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        List<MirrorTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        MirrorTransaction mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getName()).isEqualTo("CRYPTOTRANSFER");

        boolean tokenIdFound = false;

        String tokenIdString = tokenId.toString();
        for (MirrorTokenTransfer tokenTransfer : mirrorTransaction.getTokenTransfers()) {
            if (tokenTransfer.getTokenId().equalsIgnoreCase(tokenIdString)) {
                tokenIdFound = true;
                break;
            }
        }

        assertTrue(tokenIdFound);
    }

    private void verifyTokenUpdate(TokenId tokenId) {
        MirrorTokenResponse mirrorToken = verifyToken(tokenId);

        assertThat(mirrorToken.getCreatedTimestamp()).isNotEqualTo(mirrorToken.getModifiedTimestamp());
    }

    private void verifyTokenWithCustomFeesSchedule(TokenId tokenId, String createdTimestamp) {
        MirrorTokenResponse response = verifyToken(tokenId);

        MirrorCustomFees expected = new MirrorCustomFees();
        expected.setCreatedTimestamp(createdTimestamp);
        for (CustomFee customFee : tokenCustomFees.get(tokenId)) {
            if (customFee instanceof CustomFixedFee) {
                CustomFixedFee sdkFixedFee = (CustomFixedFee) customFee;
                MirrorFixedFee fixedFee = new MirrorFixedFee();

                fixedFee.setAmount(sdkFixedFee.getAmount());
                fixedFee.setCollectorAccountId(sdkFixedFee.getFeeCollectorAccountId().toString());

                if (sdkFixedFee.getDenominatingTokenId() != null) {
                    fixedFee.setDenominatingTokenId(sdkFixedFee.getDenominatingTokenId().toString());
                }

                expected.getFixedFees().add(fixedFee);
            } else {
                CustomFractionalFee sdkFractionalFee = (CustomFractionalFee) customFee;
                MirrorFractionalFee fractionalFee = new MirrorFractionalFee();

                MirrorFraction fraction = new MirrorFraction();
                fraction.setNumerator(sdkFractionalFee.getNumerator());
                fraction.setDenominator(sdkFractionalFee.getDenominator());
                fractionalFee.setAmount(fraction);

                fractionalFee.setCollectorAccountId(sdkFractionalFee.getFeeCollectorAccountId().toString());
                fractionalFee.setDenominatingTokenId(tokenId.toString());

                if (sdkFractionalFee.getMax() != 0) {
                    fractionalFee.setMaximum(sdkFractionalFee.getMax());
                }

                fractionalFee.setMinimum(sdkFractionalFee.getMin());

                expected.getFractionalFees().add(fractionalFee);
            }
        }

        MirrorCustomFees actual = response.getCustomFees();

        assertThat(actual)
                .usingRecursiveComparison(
                        RecursiveComparisonConfiguration
                                .builder()
                                .withIgnoreCollectionOrder(true)
                                .build()
                )
                .isEqualTo(expected);
    }

    private void publishBackgroundMessages() {
        // publish background message to network to reduce possibility of stale info in low TPS environment
        try {
            topicClient.publishMessageToDefaultTopic();
        } catch (Exception ex) {
            log.trace("Encountered issue published background messages to default topic", ex);
        }
    }

    private int getIndexOrDefault(Integer index) {
        return index != null ? index : 0;
    }
}
