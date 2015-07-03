/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.server.computation.measure;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;

public class MeasureKeyTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void fail_with_NPE_when_metric_key_is_null() throws Exception {
    thrown.expect(NullPointerException.class);

    new MeasureKey(null, 1, 2);
  }

  @Test
  public void fail_with_IAE_when_rule_id_is_default_value() throws Exception {
    thrown.expect(IllegalArgumentException.class);

    new MeasureKey("metricKey", -6253, 2);
  }

  @Test
  public void fail_with_IAE_when_characteristic_id_is_default_value() throws Exception {
    thrown.expect(IllegalArgumentException.class);

    new MeasureKey("metricKey", 1, -6253);
  }

  @Test
  public void test_equals() throws Exception {
    MeasureKey measureKey = new MeasureKey("metricKey", (Integer) null, null);
    MeasureKey measureKey2 = new MeasureKey("metricKey", (Integer) null, null);
    MeasureKey anotheMeasureKey = new MeasureKey("anotherMetricKey", (Integer) null, null);

    MeasureKey ruleMeasureKey = new MeasureKey("metricKey", 1, null);
    MeasureKey ruleMeasureKey2 = new MeasureKey("metricKey", 1, null);
    MeasureKey anotherRuleMeasureKey = new MeasureKey("metricKey", 2, null);

    MeasureKey characteristicMeasureKey = new MeasureKey("metricKey", null, 1);
    MeasureKey characteristicMeasureKey2 = new MeasureKey("metricKey", null, 1);
    MeasureKey anotherCharacteristicMeasureKey = new MeasureKey("metricKey", null, 2);

    assertThat(measureKey).isEqualTo(measureKey);
    assertThat(measureKey).isEqualTo(measureKey2);
    assertThat(measureKey).isNotEqualTo(null);
    assertThat(measureKey).isNotEqualTo(anotheMeasureKey);

    assertThat(ruleMeasureKey).isEqualTo(ruleMeasureKey2);
    assertThat(ruleMeasureKey).isNotEqualTo(anotherRuleMeasureKey);

    assertThat(characteristicMeasureKey).isEqualTo(characteristicMeasureKey2);
    assertThat(characteristicMeasureKey).isNotEqualTo(anotherCharacteristicMeasureKey);
  }

  @Test
  public void to_string() throws Exception {
    assertThat(new MeasureKey("metricKey", 1, 2).toString()).isEqualTo("MeasureKey{metricKey='metricKey', ruleId=1, characteristicId=2}");
    assertThat(new MeasureKey("metricKey", 1, null).toString()).isEqualTo("MeasureKey{metricKey='metricKey', ruleId=1, characteristicId=-6253}");
    assertThat(new MeasureKey("metricKey", null, 2).toString()).isEqualTo("MeasureKey{metricKey='metricKey', ruleId=-6253, characteristicId=2}");
  }
}
