package com.g2rain.iam.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IamAccessPropertiesTest {

    @Test
    void resolvedBrandNameUsesConfiguredValue() {
        IamAccessProperties properties = new IamAccessProperties();
        properties.setBrandName("  某某集团统一认证  ");
        assertEquals("某某集团统一认证", properties.resolvedBrandName());
    }

    @Test
    void resolvedBrandNameFallsBackWhenBlank() {
        IamAccessProperties properties = new IamAccessProperties();
        properties.setBrandName("   ");
        assertEquals("G2Rain IAM", properties.resolvedBrandName());
    }
}
