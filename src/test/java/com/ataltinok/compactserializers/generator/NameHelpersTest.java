package com.ataltinok.compactserializers.generator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ataltinok.compactserializers.generator.NameHelpers;

class NameHelpersTest {

	@Test
	void plainLowerCaseFieldStaysUppercase() {
		assertThat(NameHelpers.toUpperSnake("gpsi")).isEqualTo("GPSI");
	}

	@Test
	void camelCaseSplitsOnBoundaries() {
		assertThat(NameHelpers.toUpperSnake("plmnId")).isEqualTo("PLMN_ID");
		assertThat(NameHelpers.toUpperSnake("correlationId")).isEqualTo("CORRELATION_ID");
	}

	@Test
	void acronymRunKeptTogether() {
		assertThat(NameHelpers.toUpperSnake("XCORRELATIONID")).isEqualTo("XCORRELATIONID");
	}

	@Test
	void acronymFollowedByCamelToken() {
		assertThat(NameHelpers.toUpperSnake("HTTPServer")).isEqualTo("HTTP_SERVER");
		assertThat(NameHelpers.toUpperSnake("XMLParser")).isEqualTo("XML_PARSER");
	}

	@Test
	void fieldConstant() {
		assertThat(NameHelpers.fieldConstantName("plmnId")).isEqualTo("FIELD_PLMN_ID");
		assertThat(NameHelpers.fieldConstantName("gpsi")).isEqualTo("FIELD_GPSI");
	}

	@Test
	void gettersAndSetters() {
		assertThat(NameHelpers.getterName("gpsi")).isEqualTo("getGpsi");
		assertThat(NameHelpers.setterName("plmnId")).isEqualTo("setPlmnId");
		assertThat(NameHelpers.getterName("XCORRELATIONID")).isEqualTo("getXCORRELATIONID");
	}
}
