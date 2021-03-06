/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.rules.handlers;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.controller.ControllerServiceInitializationContext;
import org.apache.nifi.record.sink.RecordSinkService;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.rules.Action;
import org.apache.nifi.serialization.SimpleRecordSchema;
import org.apache.nifi.serialization.WriteResult;
import org.apache.nifi.serialization.record.DataType;
import org.apache.nifi.serialization.record.ListRecordSet;
import org.apache.nifi.serialization.record.MapRecord;
import org.apache.nifi.serialization.record.RecordField;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.serialization.record.RecordSchema;
import org.apache.nifi.serialization.record.RecordSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tags({"rules", "rules engine", "action", "action handler", "logging"})
@CapabilityDescription("Logs messages and fact information based on a provided action (usually created by a rules engine)")
public class RecordSinkHandler extends AbstractActionHandlerService{

    static final PropertyDescriptor RECORD_SINK_SERVICE = new PropertyDescriptor.Builder()
            .name("record-sink-service")
            .displayName("Record Sink Service")
            .description("Specifies the Controller Service used to support the SEND event action.  If not set SEND events will be ignored.")
            .identifiesControllerService(RecordSinkService.class)
            .required(true)
            .build();

    private RecordSinkService recordSinkService;
    private List<PropertyDescriptor> properties;

    @Override
    protected void init(ControllerServiceInitializationContext config) throws InitializationException {
        super.init(config);
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(RECORD_SINK_SERVICE);
        this.properties = Collections.unmodifiableList(properties);
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return properties;
    }

    @OnEnabled
    public void onEnabled(final ConfigurationContext context) throws InitializationException {
        if(context.getProperty(RECORD_SINK_SERVICE).isSet()) {
            recordSinkService = context.getProperty(RECORD_SINK_SERVICE).asControllerService(RecordSinkService.class);
        }
    }

    @Override
    public void execute(Action action, Map<String, Object> facts) {
        Map<String, String> attributes = action.getAttributes();
        boolean sendZeroResults = attributes.containsKey("sentZeroResults") && Boolean.parseBoolean(attributes.get("sendZeroResults"));
        final RecordSet recordSet = getRecordSet(facts);

        try {
            WriteResult result = recordSinkService.sendData(recordSet, attributes, sendZeroResults);
            if(getLogger().isDebugEnabled() && result != null){
                getLogger().debug("Records written to sink service: {}", new Object[]{result.getRecordCount()});
            }
        }catch (Exception ex){
            getLogger().warn("Exception encountered when attempting to send metrics", ex);
        }
    }

    private RecordSet getRecordSet(Map<String, Object> metrics){
        List<RecordField> recordFields = metrics.entrySet().stream().map(entry ->
                new RecordField(entry.getKey(),getDataType(String.valueOf(entry.getValue())))
        ).collect(Collectors.toList());
        final RecordSchema recordSchema = new SimpleRecordSchema(recordFields);
        return new ListRecordSet(recordSchema, Arrays.asList( new MapRecord(recordSchema,metrics)));
    }

    private DataType getDataType(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        if (NumberUtils.isParsable(value)) {
            if (value.contains(".")) {
                try {
                    final double doubleValue = Double.parseDouble(value);
                    if (doubleValue > Float.MAX_VALUE || doubleValue < Float.MIN_VALUE) {
                        return RecordFieldType.DOUBLE.getDataType();
                    }

                    return RecordFieldType.FLOAT.getDataType();
                } catch (final NumberFormatException nfe) {
                    return RecordFieldType.STRING.getDataType();
                }
            }

            try {
                final long longValue = Long.parseLong(value);
                if (longValue > Integer.MAX_VALUE || longValue < Integer.MIN_VALUE) {
                    return RecordFieldType.LONG.getDataType();
                }

                return RecordFieldType.INT.getDataType();
            } catch (final NumberFormatException nfe) {
                return RecordFieldType.STRING.getDataType();
            }
        }

        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return RecordFieldType.BOOLEAN.getDataType();
        }

        return RecordFieldType.STRING.getDataType();

    }

}
