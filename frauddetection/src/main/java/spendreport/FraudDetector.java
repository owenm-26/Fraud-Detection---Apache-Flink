/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package spendreport;

import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.walkthrough.common.entity.Alert;
import org.apache.flink.walkthrough.common.entity.Transaction;

/**
 * Skeleton code for implementing a fraud detector.
 */
public class FraudDetector extends KeyedProcessFunction<Long, Transaction, Alert> {

	private transient ValueState<Boolean> flagState;
	private transient ValueState<Long> timerState;

	@Override
	public void open(OpenContext openContext) {
//		small value before flag
		ValueStateDescriptor<Boolean> flagDescriptor = new ValueStateDescriptor<>(
				"flag",
				Types.BOOLEAN);
		flagState = getRuntimeContext().getState(flagDescriptor);

//		timer since flag set
		ValueStateDescriptor<Long> timerDescriptor = new ValueStateDescriptor<>(
				"timer-state",
				Types.LONG);
		timerState = getRuntimeContext().getState(timerDescriptor);
	}

	private static final long serialVersionUID = 1L;
	private static final double SMALL_AMOUNT = 1.00;
	private static final double LARGE_AMOUNT = 500.00;
	private static final long ONE_MINUTE = 60 * 1000;

	@Override
	public void onTimer(long timestamp, OnTimerContext ctx, Collector<Alert> out) {
		// remove flag after 1 minute
		timerState.clear();
		flagState.clear();
	}

	private void cleanUp(Context ctx) throws Exception {
		// delete timer
		Long timer = timerState.value();
		ctx.timerService().deleteProcessingTimeTimer(timer);

		// clean up all state
		timerState.clear();
		flagState.clear();
	}

	@Override
	public void processElement(
			Transaction transaction,
			Context context,
			Collector<Alert> collector) throws Exception {

//		Get state var
		Boolean lastTransactionSmall = flagState.value();

//		check if fraud
		if (lastTransactionSmall == true && transaction.getAmount() > LARGE_AMOUNT){
			Alert alert = new Alert();
			alert.setId(transaction.getAccountId());

			collector.collect(alert);
		}

//		clear flag and timer
		cleanUp(context);

//		Check if small
		if (transaction.getAmount() < SMALL_AMOUNT){
			flagState.update(true);

//			set timer and timerState
			long timer = context.timerService().currentProcessingTime() + ONE_MINUTE;
			context.timerService().registerProcessingTimeTimer(timer);
			timerState.update(timer);
		}


	}
}
