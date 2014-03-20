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

package org.sonar.server.debt;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.junit.Test;
import org.sonar.api.technicaldebt.batch.internal.DefaultCharacteristic;

import java.io.IOException;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;

public class DebtCharacteristicsXMLImporterTest {

  @Test
  public void import_characteristics() {
    String xml = getFileContent("import_characteristics.xml");

    DebtCharacteristicsXMLImporter.DebtModel debtModel = new DebtCharacteristicsXMLImporter().importXML(xml);

    assertThat(debtModel.rootCharacteristics()).hasSize(2);
    assertThat(debtModel.rootCharacteristics().get(0).key()).isEqualTo("PORTABILITY");
    assertThat(debtModel.rootCharacteristics().get(1).key()).isEqualTo("MAINTAINABILITY");

    DefaultCharacteristic portability = debtModel.characteristicByKey("PORTABILITY");
    assertThat(portability.order()).isEqualTo(1);
    assertThat(portability.children()).hasSize(2);
    assertThat(portability.children().get(0).key()).isEqualTo("COMPILER_RELATED_PORTABILITY");
    assertThat(debtModel.characteristicByKey("COMPILER_RELATED_PORTABILITY").parent().key()).isEqualTo("PORTABILITY");
    assertThat(portability.children().get(1).key()).isEqualTo("HARDWARE_RELATED_PORTABILITY");
    assertThat(debtModel.characteristicByKey("HARDWARE_RELATED_PORTABILITY").parent().key()).isEqualTo("PORTABILITY");

    DefaultCharacteristic maintainability = debtModel.characteristicByKey("MAINTAINABILITY");
    assertThat(maintainability.order()).isEqualTo(2);
    assertThat(maintainability.children()).hasSize(1);
    assertThat(maintainability.children().get(0).key()).isEqualTo("READABILITY");
    assertThat(debtModel.characteristicByKey("READABILITY").parent().key()).isEqualTo("MAINTAINABILITY");
  }

  @Test
  public void import_badly_formatted_xml() {
    String xml = getFileContent("import_badly_formatted_xml.xml");

    DebtCharacteristicsXMLImporter.DebtModel debtModel = new DebtCharacteristicsXMLImporter().importXML(xml);

    // characteristics
    assertThat(debtModel.rootCharacteristics()).hasSize(2);
    DefaultCharacteristic efficiency = debtModel.characteristicByKey("EFFICIENCY");
    assertThat(efficiency.name()).isEqualTo("Efficiency");

    // sub-characteristics
    assertThat(efficiency.children()).hasSize(1);
    DefaultCharacteristic memoryEfficiency = debtModel.characteristicByKey("MEMORY_EFFICIENCY");
    assertThat(memoryEfficiency.name()).isEqualTo("Memory use");
  }

  @Test
  public void fail_on_bad_xml() {
    String xml = getFileContent("fail_on_bad_xml.xml");

    try {
      new DebtCharacteristicsXMLImporter().importXML(xml);
      fail();
    } catch (Exception e){
      assertThat(e).isInstanceOf(IllegalStateException.class);
    }
  }

  private String getFileContent(String file) {
    try {
      return Resources.toString(Resources.getResource(DebtCharacteristicsXMLImporterTest.class, "DebtCharacteristicsXMLImporterTest/" + file), Charsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
