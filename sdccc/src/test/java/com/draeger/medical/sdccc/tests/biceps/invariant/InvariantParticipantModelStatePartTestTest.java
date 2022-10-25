/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.tests.biceps.invariant;

import com.draeger.medical.biceps.model.participant.ComponentActivation;
import com.draeger.medical.biceps.model.participant.MetricAvailability;
import com.draeger.medical.biceps.model.participant.MetricCategory;
import com.draeger.medical.dpws.soap.model.Envelope;
import com.draeger.medical.sdccc.marshalling.MarshallingUtil;
import com.draeger.medical.sdccc.messages.Message;
import com.draeger.medical.sdccc.messages.MessageStorage;
import com.draeger.medical.sdccc.sdcri.testclient.TestClient;
import com.draeger.medical.sdccc.sdcri.testclient.TestClientUtil;
import com.draeger.medical.sdccc.tests.InjectorTestBase;
import com.draeger.medical.sdccc.tests.test_util.InjectorUtil;
import com.draeger.medical.sdccc.tests.util.NoTestData;
import com.draeger.medical.sdccc.util.CertificateUtil;
import com.draeger.medical.sdccc.util.Constants;
import com.draeger.medical.sdccc.util.MdibBuilder;
import com.draeger.medical.sdccc.util.MessageBuilder;
import com.draeger.medical.sdccc.util.MessageStorageUtil;
import com.draeger.medical.t2iapi.ResponseTypes;
import com.google.common.collect.ArrayListMultimap;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.somda.sdc.dpws.CommunicationLog;
import org.somda.sdc.dpws.helper.JaxbMarshalling;
import org.somda.sdc.dpws.soap.CommunicationContext;
import org.somda.sdc.dpws.soap.HttpApplicationInfo;
import org.somda.sdc.dpws.soap.SoapMarshalling;
import org.somda.sdc.dpws.soap.TransportInfo;
import org.somda.sdc.glue.common.ActionConstants;

import javax.xml.bind.JAXBElement;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for the BICEPS {@linkplain InvariantParticipantModelStatePartTest}.
 */
public class InvariantParticipantModelStatePartTestTest {

    private static final String VMD_HANDLE = "someVmd";
    private static final String CHANNEL_HANDLE = "someChannel";
    private static final String MSRMT_METRIC_HANDLE = "someMsrmtStringMetric";
    private static final String MSRMT_METRIC_HANDLE2 = "someMsrmtStringMetric2";
    private static final String SET_METRIC_HANDLE = "someSetStringMetric";
    private static final String SET_METRIC_HANDLE2 = "someSetStringMetric2";
    private static final String SEQUENCE_ID = MdibBuilder.DEFAULT_SEQUENCE_ID;

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private static final long TIMESTAMP_START = 1000;
    private static final long TIMESTAMP_START2 = 1200;
    private static final long TIMESTAMP_FINISH = 2000;
    private static final long TIMESTAMP_FINISH2 = 1800;
    private static final long TIMESTAMP_IN_INTERVAL = 1500;
    private static final long TIMESTAMP_IN_INTERVAL2 = 1550;
    private static final long TIMESTAMP_NOT_IN_INTERVAL = TimeUnit.NANOSECONDS.convert(10, TimeUnit.SECONDS);
    private static MessageStorageUtil messageStorageUtil;
    private static MdibBuilder mdibBuilder;
    private static MessageBuilder messageBuilder;
    private static com.draeger.medical.sdccc.marshalling.SoapMarshalling marshalling2;
    private MessageStorage storage;
    private InvariantParticipantModelStatePartTest testClass;
    private JaxbMarshalling baseMarshalling;
    private SoapMarshalling marshalling;


    @BeforeAll
    static void setupMarshalling() {
        final Injector marshallingInjector = MarshallingUtil.createMarshallingTestInjector(true);
        messageStorageUtil = marshallingInjector.getInstance(MessageStorageUtil.class);
        mdibBuilder = marshallingInjector.getInstance(MdibBuilder.class);
        messageBuilder = marshallingInjector.getInstance(MessageBuilder.class);
        marshalling2 = marshallingInjector.getInstance(com.draeger.medical.sdccc.marshalling.SoapMarshalling.class);
    }

    @BeforeEach
    void setUp() throws IOException, TimeoutException {
        final TestClient mockClient = mock(TestClient.class);
        when(mockClient.isClientRunning()).thenReturn(true);

        final Injector injector = InjectorUtil.setupInjector(
            new AbstractModule() {
                @Override
                protected void configure() {
                    bind(TestClient.class).toInstance(mockClient);
                }
            }
        );
        InjectorTestBase.setInjector(injector);

        final var riInjector = TestClientUtil.createClientInjector(
        );
        when(mockClient.getInjector()).thenReturn(riInjector);

        baseMarshalling = riInjector.getInstance(JaxbMarshalling.class);
        baseMarshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        marshalling = riInjector.getInstance(SoapMarshalling.class);
        marshalling.startAsync().awaitRunning(DEFAULT_TIMEOUT);

        storage = injector.getInstance(MessageStorage.class);

        testClass = new InvariantParticipantModelStatePartTest();
        testClass.setUp();
    }

    @AfterEach
    void tearDown() throws TimeoutException {
        baseMarshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        marshalling.stopAsync().awaitTerminated(DEFAULT_TIMEOUT);
        storage.close();
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement54700NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement54700);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54700NoSuccessfulManipulation() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.ON.value()));
        // add manipulation data with result fail
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, ResponseTypes.Result.RESULT_FAIL,
            Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54700);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'Msrmt' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54700BadWrongMetricCategory() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.ON.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        // no manipulation with category msrmt in storage
        final var error = assertThrows(NoTestData.class, testClass::testRequirement54700);
        assertTrue(error.getMessage().contains(
            String.format(InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION,
                MetricCategory.MSRMT)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'Msrmt' a metric report containing the manipulated metric with the expected activation state exists
     * and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54700Good() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.ON.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result,
            Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        testClass.testRequirement54700();
    }

    /**
     * Tests whether the test correctly retrieves the first relevant report in the time interval for each manipulation
     * data with category 'Msrmt'.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54700GoodOverlappingTimeInterval() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.ON.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH,
            ResponseTypes.Result.RESULT_SUCCESS, Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final List<Pair<String, String>> parameters2 = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE2),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.ON.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START2, TIMESTAMP_FINISH2,
            ResponseTypes.Result.RESULT_SUCCESS, Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters2);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));
        final var metricReport2 = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE2, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));

        testClass.testRequirement54700();
    }

    /**
     * Tests whether the test fails, when no metric report is present in the time interval of a manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54700BadNoMetricReportFollowingManipulation() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION, ComponentActivation.ON.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        // this metric report is not in the time interval of the setMetricStatus manipulation
        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_NOT_IN_INTERVAL, metricReport));

        final var error = assertThrows(AssertionError.class, testClass::testRequirement54700);
        assertTrue(error.getCause() instanceof NoTestData);
        assertTrue(error.getCause().getMessage().contains(String.format(InvariantParticipantModelStatePartTest
            .NO_REPORT_IN_TIME_INTERVAL, methodName, TIMESTAMP_START, TIMESTAMP_FINISH)));
    }

    /**
     * Tests whether the test fails, when no reports with the expected handle from the manipulation data are in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54700NoReportsWithExpectedHandle() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.ON.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result,
            Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE2, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var error = assertThrows(AssertionError.class, testClass::testRequirement54700);
        assertTrue(error.getMessage().contains(
            String.format(InvariantParticipantModelStatePartTest.NO_REPORT_WITH_EXPECTED_HANDLE, MSRMT_METRIC_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric from the manipulation data has the wrong activation state in the
     * following metric report.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54700BadWrongActivationInFollowingReport() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION, ComponentActivation.ON.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        // activation state should be on
        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var error = assertThrows(AssertionError.class, testClass::testRequirement54700);
        assertTrue(error.getMessage().contains(String.format(
            InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE, MSRMT_METRIC_HANDLE,
            ComponentActivation.ON, ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test retrieves the first metric report in the time interval of a manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54700GoodMultipleReportsInInterval() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.ON.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        // good report in time interval
        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.ON);
        // should not fail the test, since the first report in the time interval is relevant for the test
        final var metricReport2 = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);

        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement54700();
    }

    /**
     * Tests whether the test do not pass when the first report in the time interval is bad, even if followed by a
     * report that would pass the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54700BadMultipleReportsInInterval() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.ON.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.ON);
        final var metricReport2 = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);

        // the first report in the time interval has the wrong activation state
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport));

        assertThrows(AssertionError.class, testClass::testRequirement54700);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement5472NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement5472);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5472NoSuccessfulManipulation() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));
        // add manipulation data with result fail
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, ResponseTypes.Result.RESULT_FAIL,
            Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.STND_BY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5472);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'Msrmt' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5472BadWrongMetricCategory() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.STND_BY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        // no manipulation with category msrmt in storage
        final var error = assertThrows(NoTestData.class, testClass::testRequirement5472);
        assertTrue(error.getMessage().contains(
            String.format(InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION,
                MetricCategory.MSRMT)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'Msrmt' a metric report containing the manipulated metric with the expected activation state exists
     * and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5472Good() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result,
            Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.STND_BY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        testClass.testRequirement5472();
    }

    /**
     * Tests whether the test correctly retrieves the first relevant report in the time interval for each manipulation
     * data with category 'Msrmt'.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5472GoodOverlappingTimeInterval() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));

        final List<Pair<String, String>> parameters2 = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE2),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));

        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH,
            ResponseTypes.Result.RESULT_SUCCESS, Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters2);
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START2, TIMESTAMP_FINISH2,
            ResponseTypes.Result.RESULT_SUCCESS, Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.STND_BY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));
        final var metricReport2 = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE2, ComponentActivation.STND_BY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement5472();
    }

    /**
     * Tests whether the test fails, when no metric report is present in the time interval of a manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5472BadNoMetricReportFollowingManipulation() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        // this metric report is not in the time interval of the setMetricStatus manipulation
        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.STND_BY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_NOT_IN_INTERVAL, metricReport));

        final var error = assertThrows(AssertionError.class, testClass::testRequirement5472);
        assertTrue(error.getCause() instanceof NoTestData);
        assertTrue(error.getCause().getMessage().contains(String.format(InvariantParticipantModelStatePartTest
            .NO_REPORT_IN_TIME_INTERVAL, methodName, TIMESTAMP_START, TIMESTAMP_FINISH)));
    }

    /**
     * Tests whether the test fails, when no reports with the expected handle from the manipulation data are in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5472NoReportsWithExpectedHandle() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result,
            Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE2, ComponentActivation.STND_BY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var error = assertThrows(AssertionError.class, testClass::testRequirement5472);
        assertTrue(error.getMessage().contains(
            String.format(InvariantParticipantModelStatePartTest.NO_REPORT_WITH_EXPECTED_HANDLE, MSRMT_METRIC_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric from the manipulation data has the wrong activation state in the
     * following metric report.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5472BadWrongActivationInFollowingReport() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        // activation state should be stndby
        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var error = assertThrows(AssertionError.class, testClass::testRequirement5472);
        assertTrue(error.getMessage().contains(String.format(
            InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE, MSRMT_METRIC_HANDLE,
            ComponentActivation.STND_BY, ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test retrieves the first metric report in the time interval of a manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5472GoodMultipleReportsInInterval() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        // good report in time interval
        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.STND_BY);
        // should not fail the test, since the first report in the time interval is relevant for the test
        final var metricReport2 = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement5472();
    }

    /**
     * Tests whether the test do not pass when the first report in the time interval is bad, even if followed by a
     * report that would pass the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5472BadMultipleReportsInInterval() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.STND_BY);
        final var metricReport2 = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);

        // the first report in the time interval has the wrong activation state
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport));

        assertThrows(AssertionError.class, testClass::testRequirement5472);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement5474NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement5474);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5474NoSuccessfulManipulation() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        // add manipulation data with result fail
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, ResponseTypes.Result.RESULT_FAIL,
            Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5474);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'Msrmt' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5474BadWrongMetricCategory() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        // no manipulation with category msrmt in storage
        final var error = assertThrows(NoTestData.class, testClass::testRequirement5474);
        assertTrue(error.getMessage().contains(
            String.format(InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION,
                MetricCategory.MSRMT)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'Msrmt' a metric report containing the manipulated metric with the expected activation state exists
     * and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5474Good() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result,
            Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        testClass.testRequirement5474();
    }

    /**
     * Tests whether the test correctly retrieves the first relevant report in the time interval for each manipulation
     * data with category 'Msrmt'.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5474GoodOverlappingTimeInterval() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));

        final List<Pair<String, String>> parameters2 = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE2),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH,
            ResponseTypes.Result.RESULT_SUCCESS, Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters2);

        messageStorageUtil.addManipulation(storage, TIMESTAMP_START2, TIMESTAMP_FINISH2,
            ResponseTypes.Result.RESULT_SUCCESS, Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));
        final var metricReport2 = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE2, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement5474();
    }

    /**
     * Tests whether the test fails, when no metric report is present in the time interval of a manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5474BadNoMetricReportFollowingManipulation() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        // this metric report is not in the time interval of the setMetricStatus manipulation
        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_NOT_IN_INTERVAL, metricReport));

        final var error = assertThrows(AssertionError.class, testClass::testRequirement5474);
        assertTrue(error.getCause() instanceof NoTestData);
        assertTrue(error.getCause().getMessage().contains(String.format(
            InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME_INTERVAL, methodName, TIMESTAMP_START,
            TIMESTAMP_FINISH)));
    }

    /**
     * Tests whether the test fails, when no reports with the expected handle from the manipulation data are in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5474NoReportsWithExpectedHandle() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result,
            Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE2, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var error = assertThrows(AssertionError.class, testClass::testRequirement5474);
        assertTrue(error.getMessage().contains(
            String.format(InvariantParticipantModelStatePartTest.NO_REPORT_WITH_EXPECTED_HANDLE, MSRMT_METRIC_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric from the manipulation data has the wrong activation state in the
     * following metric report.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5474BadWrongActivationInFollowingReport() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        // activation state should be OFF
        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var error = assertThrows(AssertionError.class, testClass::testRequirement5474);
        assertTrue(error.getMessage().contains(String.format(
            InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE, MSRMT_METRIC_HANDLE,
            ComponentActivation.OFF, ComponentActivation.ON)));
    }

    /**
     * Tests whether the test retrieves the first metric report in the time interval of a manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5474GoodMultipleReportsInInterval() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        // good report in time interval
        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        // should not fail the test, since the first report in the time interval is relevant for the test
        final var metricReport2 = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement5474();
    }

    /**
     * Tests whether the test do not pass when the first report in the time interval is bad, even if followed by a
     * report that would pass the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5474BadMultipleReportsInInterval() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.OFF);
        final var metricReport2 = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, MSRMT_METRIC_HANDLE, ComponentActivation.ON);

        // the first report in the time interval has the wrong activation state
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport));

        assertThrows(AssertionError.class, testClass::testRequirement5474);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement54760NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement54760);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54760NoSuccessfulManipulation() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.ON.value()));
        // add manipulation data with result fail
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, ResponseTypes.Result.RESULT_FAIL,
            Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54760);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'Set' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54760BadWrongMetricCategory() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.ON.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        // no manipulation with category msrmt in storage
        final var error = assertThrows(NoTestData.class, testClass::testRequirement54760);
        assertTrue(error.getMessage().contains(
            String.format(InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION,
                MetricCategory.SET)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'Set' a metric report containing the manipulated metric with the expected activation state exists
     * and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54760Good() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.ON.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result,
            Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        testClass.testRequirement54760();
    }

    /**
     * Tests whether the test correctly retrieves the first relevant report in the time interval for each manipulation
     * data with category 'Set'.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54760GoodOverlappingTimeInterval() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.ON.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH,
            ResponseTypes.Result.RESULT_SUCCESS, Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);
        final List<Pair<String, String>> parameters2 = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE2),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.ON.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START2, TIMESTAMP_FINISH2,
            ResponseTypes.Result.RESULT_SUCCESS, Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters2);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));
        final var metricReport2 = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE2, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement54760();
    }

    /**
     * Tests whether the test fails, when no metric report is present in the time interval of a manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54760BadNoMetricReportFollowingManipulation() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION, ComponentActivation.ON.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        // this metric report is not in the time interval of the setMetricStatus manipulation
        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_NOT_IN_INTERVAL, metricReport));

        final var error = assertThrows(AssertionError.class, testClass::testRequirement54760);
        assertTrue(error.getCause() instanceof NoTestData);
        assertTrue(error.getCause().getMessage().contains(
            String.format(InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME_INTERVAL, methodName,
                TIMESTAMP_START, TIMESTAMP_FINISH)));
    }

    /**
     * Tests whether the test fails, when no reports with the expected handle from the manipulation data are in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54760NoReportsWithExpectedHandle() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.ON.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result,
            Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE2, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var error = assertThrows(AssertionError.class, testClass::testRequirement54760);
        assertTrue(error.getMessage().contains(
            String.format(InvariantParticipantModelStatePartTest.NO_REPORT_WITH_EXPECTED_HANDLE, SET_METRIC_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric from the manipulation data has the wrong activation state in the
     * following metric report.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54760BadWrongActivationInFollowingReport() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION, ComponentActivation.ON.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        // activation state should be on
        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var error = assertThrows(AssertionError.class, testClass::testRequirement54760);
        assertTrue(error.getMessage().contains(String.format(
            InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE, SET_METRIC_HANDLE,
            ComponentActivation.ON, ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test retrieves the first metric report in the time interval of a manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54760GoodMultipleReportsInInterval() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.ON.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        // good report in time interval
        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.ON);
        // should not fail the test, since the first report in the time interval is relevant for the test
        final var metricReport2 = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement54760();
    }

    /**
     * Tests whether the test do not pass when the first report in the time interval is bad, even if followed by a
     * report that would pass the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54760BadMultipleReportsInInterval() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.ON.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.ON);
        final var metricReport2 = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);

        // the first report in the time interval has the wrong activation state
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport));

        assertThrows(AssertionError.class, testClass::testRequirement54760);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement5478NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement5478);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5478NoSuccessfulManipulation() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));
        // add manipulation data with result fail
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, ResponseTypes.Result.RESULT_FAIL,
            Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.STND_BY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var error = assertThrows(NoTestData.class, testClass::testRequirement5478);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION));
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'Set' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5478BadWrongMetricCategory() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.STND_BY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        // no manipulation with category set in storage
        final var error = assertThrows(NoTestData.class, testClass::testRequirement5478);
        assertTrue(error.getMessage().contains(
            String.format(InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION,
                MetricCategory.SET)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'Set' a metric report containing the manipulated metric with the expected activation state exists
     * and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5478Good() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result,
            Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.STND_BY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        testClass.testRequirement5478();
    }

    /**
     * Tests whether the test correctly retrieves the first relevant report in the time interval for each manipulation
     * data with category 'Set'.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5478GoodOverlappingTimeInterval() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH,
            ResponseTypes.Result.RESULT_SUCCESS, Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final List<Pair<String, String>> parameters2 = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE2),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START2, TIMESTAMP_FINISH2,
            ResponseTypes.Result.RESULT_SUCCESS, Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters2);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.STND_BY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var metricReport2 = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE2, ComponentActivation.STND_BY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement5478();
    }

    /**
     * Tests whether the test fails, when no metric report is present in the time interval of a manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5478BadNoMetricReportFollowingManipulation() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        // this metric report is not in the time interval of the setMetricStatus manipulation
        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.STND_BY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_NOT_IN_INTERVAL, metricReport));

        final var error = assertThrows(AssertionError.class, testClass::testRequirement5478);
        assertTrue(error.getCause() instanceof NoTestData);
        assertTrue(error.getCause().getMessage().contains(String.format(
            InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME_INTERVAL, methodName, TIMESTAMP_START,
            TIMESTAMP_FINISH)));
    }

    /**
     * Tests whether the test fails, when no reports with the expected handle from the manipulation data are in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5478NoReportsWithExpectedHandle() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result,
            Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE2, ComponentActivation.STND_BY);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var error = assertThrows(AssertionError.class, testClass::testRequirement5478);
        assertTrue(error.getMessage().contains(
            String.format(InvariantParticipantModelStatePartTest.NO_REPORT_WITH_EXPECTED_HANDLE, SET_METRIC_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric from the manipulation data has the wrong activation state in the
     * following metric report.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5478BadWrongActivationInFollowingReport() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        // activation state should be stndby
        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var error = assertThrows(AssertionError.class, testClass::testRequirement5478);
        assertTrue(error.getMessage().contains(String.format(
            InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE, SET_METRIC_HANDLE,
            ComponentActivation.STND_BY, ComponentActivation.OFF)));
    }

    /**
     * Tests whether the test retrieves the first metric report in the time interval of a manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5478GoodMultipleReportsInInterval() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        // good report in time interval
        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.STND_BY);
        // should not fail the test, since the first report in the time interval is relevant for the test
        final var metricReport2 = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement5478();
    }

    /**
     * Tests whether the test do not pass when the first report in the time interval is bad, even if followed by a
     * report that would pass the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement5478BadMultipleReportsInInterval() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.STND_BY.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.STND_BY);
        final var metricReport2 = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);

        // the first report in the time interval has the wrong activation state
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport));

        assertThrows(AssertionError.class, testClass::testRequirement5478);
    }

    /**
     * Tests whether no test data fails the test.
     */
    @Test
    public void testRequirement54710NoTestData() {
        assertThrows(NoTestData.class, testClass::testRequirement54710);
    }

    /**
     * Tests whether the test fails when no manipulation data with ResponseTypes.Result.RESULT_SUCCESS is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54710NoSuccessfulManipulation() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        // add manipulation data with result fail
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, ResponseTypes.Result.RESULT_FAIL,
            Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var error = assertThrows(NoTestData.class, testClass::testRequirement54710);
        assertTrue(error.getMessage().contains(InvariantParticipantModelStatePartTest.NO_SUCCESSFUL_MANIPULATION),
            error.getMessage());
    }

    /**
     * Test whether the test fails, when no manipulation data with category 'Set' is in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54710BadWrongMetricCategory() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, MSRMT_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.MSRMT.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        // no manipulation with category SET in storage
        final var error = assertThrows(NoTestData.class, testClass::testRequirement54710);
        assertTrue(error.getMessage().contains(
            String.format(InvariantParticipantModelStatePartTest.NO_SET_METRIC_STATUS_MANIPULATION,
                MetricCategory.SET)));
    }

    /**
     * Tests whether the test passes, when for each manipulation data for 'setMetricStatus' manipulations and metrics
     * with category 'Set' a metric report containing the manipulated metric with the expected activation state exists
     * and is in the time interval of the manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54710Good() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result,
            Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        testClass.testRequirement54710();
    }

    /**
     * Tests whether the test correctly retrieves the first relevant report in the time interval for each manipulation
     * data with category 'Set'.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54710GoodOverlappingTimeInterval() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH,
            ResponseTypes.Result.RESULT_SUCCESS, Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final List<Pair<String, String>> parameters2 = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE2),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START2, TIMESTAMP_FINISH2,
            ResponseTypes.Result.RESULT_SUCCESS, Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters2);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));
        final var metricReport2 = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE2, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement54710();
    }

    /**
     * Tests whether the test fails, when no metric report is present in the time interval of a manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54710BadNoMetricReportFollowingManipulation() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        // this metric report is not in the time interval of the setMetricStatus manipulation
        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_NOT_IN_INTERVAL, metricReport));

        final var error = assertThrows(AssertionError.class, testClass::testRequirement54710);
        assertTrue(error.getCause() instanceof NoTestData);
        assertTrue(error.getCause().getMessage().contains(String.format(
            InvariantParticipantModelStatePartTest.NO_REPORT_IN_TIME_INTERVAL, methodName, TIMESTAMP_START,
            TIMESTAMP_FINISH)));
    }

    /**
     * Tests whether the test fails, when no reports with the expected handle from the manipulation data are in storage.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54710NoReportsWithExpectedHandle() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result,
            Constants.MANIPULATION_NAME_SET_METRIC_STATUS, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE2, ComponentActivation.OFF);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));
        final var error = assertThrows(AssertionError.class, testClass::testRequirement5478);
        assertTrue(error.getMessage().contains(
            String.format(InvariantParticipantModelStatePartTest.NO_REPORT_WITH_EXPECTED_HANDLE, SET_METRIC_HANDLE)));
    }

    /**
     * Tests whether the test fails, when the metric from the manipulation data has the wrong activation state in the
     * following metric report.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54710BadWrongActivationInFollowingReport() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        // activation state should be OFF
        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));

        final var error = assertThrows(AssertionError.class, testClass::testRequirement54710);
        assertTrue(error.getMessage().contains(String.format(
            InvariantParticipantModelStatePartTest.WRONG_ACTIVATION_STATE, SET_METRIC_HANDLE,
            ComponentActivation.OFF, ComponentActivation.ON)));
    }

    /**
     * Tests whether the test retrieves the first metric report in the time interval of a manipulation data.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54710GoodMultipleReportsInInterval() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        // good report in time interval
        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        // should not fail the test, since the first report in the time interval is relevant for the test
        final var metricReport2 = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.ON);
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport));
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport2));

        testClass.testRequirement54710();
    }

    /**
     * Tests whether the test do not pass when the first report in the time interval is bad, even if followed by a
     * report that would pass the test.
     *
     * @throws Exception on any exception
     */
    @Test
    public void testRequirement54710BadMultipleReportsInInterval() throws Exception {
        final var initial = buildMdib(SEQUENCE_ID);
        messageStorageUtil.addInboundSecureHttpMessage(storage, initial);

        final var result = ResponseTypes.Result.RESULT_SUCCESS;
        final var methodName = Constants.MANIPULATION_NAME_SET_METRIC_STATUS;
        final List<Pair<String, String>> parameters = List.of(
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_HANDLE, SET_METRIC_HANDLE),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_METRIC_CATEGORY, MetricCategory.SET.value()),
            new ImmutablePair<>(Constants.MANIPULATION_PARAMETER_COMPONENT_ACTIVATION,
                ComponentActivation.OFF.value()));
        messageStorageUtil.addManipulation(storage, TIMESTAMP_START, TIMESTAMP_FINISH, result, methodName, parameters);

        final var metricReport = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.OFF);
        final var metricReport2 = buildMetricReport(SEQUENCE_ID, BigInteger.ONE,
            BigInteger.ONE, SET_METRIC_HANDLE, ComponentActivation.ON);

        // the first report in the time interval has the wrong activation state
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL, metricReport2));
        messageStorageUtil.addMessage(storage, buildTestMessage(TIMESTAMP_IN_INTERVAL2, metricReport));

        assertThrows(AssertionError.class, testClass::testRequirement54710);
    }

    private Message buildTestMessage(final long timestamp, final Envelope envelope) throws Exception {
        final var message = mock(Message.class);
        when(message.getDirection()).thenReturn(CommunicationLog.Direction.INBOUND);
        when(message.getNanoTimestamp()).thenReturn(timestamp);
        when(message.getMessageType()).thenReturn(CommunicationLog.MessageType.RESPONSE);
        when(message.getFinalMemory()).thenReturn(getMessageBytes(envelope));
        when(message.getCommunicationContext()).thenReturn(createCommunicationContext());
        return message;
    }

    private CommunicationContext createCommunicationContext() throws CertificateException, IOException {
        final CommunicationContext messageContext = new CommunicationContext(
            new HttpApplicationInfo(ArrayListMultimap.create(), "", ""),
            new TransportInfo(
                "https",
                null, null,
                null, null,
                List.of(CertificateUtil.getDummyCert())
            )
        );
        return messageContext;
    }

    private byte[] getMessageBytes(final Envelope message) throws Exception {
        final JAXBElement<Envelope> envelope = messageBuilder.buildEnvelope(message);
        try (final var messageStream = new ByteArrayOutputStream()) {
            marshalling2.marshal(envelope, messageStream);
            return new ByteArrayInputStream(messageStream.toByteArray()).readAllBytes();
        }
    }

    private Envelope buildMdib(final String sequenceId) {
        final var mdib = mdibBuilder.buildMinimalMdib(sequenceId);

        final var mdState = mdib.getMdState();
        mdState.setStateVersion(BigInteger.ZERO);
        final var mdsDescriptor = mdib.getMdDescription().getMds().get(0);
        mdsDescriptor.setDescriptorVersion(BigInteger.ZERO);

        final var vmd = mdibBuilder.buildVmd(VMD_HANDLE);
        vmd.getLeft().setDescriptorVersion(BigInteger.ZERO);
        vmd.getRight().setDescriptorVersion(BigInteger.ZERO);
        mdsDescriptor.getVmd().add(vmd.getLeft());
        mdState.getState().add(vmd.getRight());

        final var channel = mdibBuilder.buildChannel(CHANNEL_HANDLE);
        channel.getRight().setOperatingHours(0L);
        vmd.getLeft().getChannel().add(channel.getLeft());
        mdState.getState().add(channel.getRight());

        final var msrmtMetric = mdibBuilder.buildStringMetric(MSRMT_METRIC_HANDLE,
            MetricCategory.MSRMT, MetricAvailability.INTR, mdibBuilder.buildCodedValue("abc"));
        msrmtMetric.getRight().setActivationState(ComponentActivation.OFF);
        msrmtMetric.getRight().setMetricValue(mdibBuilder.buildStringMetricValue("msrmtOne"));
        channel.getLeft().setMetric(List.of(msrmtMetric.getLeft()));
        mdState.getState().add(msrmtMetric.getRight());

        final var msrmtMetric2 = mdibBuilder.buildStringMetric(MSRMT_METRIC_HANDLE2,
            MetricCategory.MSRMT, MetricAvailability.CONT, mdibBuilder.buildCodedValue("def"));
        msrmtMetric2.getRight().setActivationState(ComponentActivation.OFF);
        msrmtMetric2.getRight().setMetricValue(mdibBuilder.buildStringMetricValue("msrmtTwo"));
        channel.getLeft().setMetric(List.of(msrmtMetric2.getLeft()));
        mdState.getState().add(msrmtMetric2.getRight());

        final var clcMetric = mdibBuilder.buildStringMetric(SET_METRIC_HANDLE,
            MetricCategory.CLC, MetricAvailability.INTR, mdibBuilder.buildCodedValue("abc"));
        clcMetric.getRight().setActivationState(ComponentActivation.OFF);
        clcMetric.getRight().setMetricValue(mdibBuilder.buildStringMetricValue("clcOne"));
        channel.getLeft().setMetric(List.of(clcMetric.getLeft()));
        mdState.getState().add(clcMetric.getRight());

        final var clcMetric2 = mdibBuilder.buildStringMetric(SET_METRIC_HANDLE2,
            MetricCategory.CLC, MetricAvailability.CONT, mdibBuilder.buildCodedValue("def"));
        clcMetric2.getRight().setActivationState(ComponentActivation.OFF);
        clcMetric2.getRight().setMetricValue(mdibBuilder.buildStringMetricValue("clcTwo"));
        channel.getLeft().setMetric(List.of(clcMetric2.getLeft()));
        mdState.getState().add(clcMetric2.getRight());

        final var getMdibResponse = messageBuilder.buildGetMdibResponse(mdib.getSequenceId());
        getMdibResponse.setMdib(mdib);

        return messageBuilder.createSoapMessageWithBody(
            ActionConstants.getResponseAction(ActionConstants.ACTION_GET_MDIB),
            getMdibResponse
        );
    }

    private Envelope buildMetricReport(
        final String sequenceId,
        final BigInteger mdibVersion,
        final BigInteger metricVersion,
        final String metricHandle,
        final ComponentActivation activation
    ) {
        final var report = messageBuilder.buildEpisodicMetricReport(sequenceId);

        final var metricState = mdibBuilder.buildStringMetricState(metricHandle);
        metricState.setStateVersion(metricVersion);
        metricState.setActivationState(activation);

        final var reportPart = messageBuilder.buildAbstractMetricReportReportPart();
        reportPart.getMetricState().add(metricState);

        report.setMdibVersion(mdibVersion);
        report.getReportPart().add(reportPart);
        return messageBuilder.createSoapMessageWithBody(
            ActionConstants.ACTION_EPISODIC_METRIC_REPORT,
            report
        );
    }
}