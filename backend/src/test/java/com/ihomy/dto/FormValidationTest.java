package com.ihomy.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 表单长度/精度前置校验:按列宽与 DECIMAL(14,2) 拦截越界输入,避免落库抛约束异常走兜底 500。
 * 直接起 jakarta 校验器(不起 Spring 上下文),同时覆盖「空值不拦截」以保证保险箱局部更新语义。
 */
class FormValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private Set<String> invalidFields(Object bean) {
        return validator.validate(bean).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static String repeat(String unit, int count) {
        return unit.repeat(count);
    }

    @Test
    void vaultFieldsBoundedByColumnWidth() {
        VaultItemDTO ok = new VaultItemDTO();
        ok.setName(repeat("名", 100));
        ok.setUsername(repeat("u", 200));
        ok.setPassword(repeat("p", 128));
        ok.setUrl(repeat("u", 500));
        ok.setTags(repeat("t", 200));
        ok.setNote(repeat("备", 1000));
        ok.setCategory(repeat("c", 30));
        ok.setVisibility(repeat("v", 20));
        assertThat(invalidFields(ok)).isEmpty();

        VaultItemDTO over = new VaultItemDTO();
        over.setName(repeat("名", 101));
        over.setUsername(repeat("u", 201));
        over.setPassword(repeat("p", 129));
        over.setUrl(repeat("u", 501));
        over.setTags(repeat("t", 201));
        over.setNote(repeat("备", 1001));
        over.setCategory(repeat("c", 31));
        over.setVisibility(repeat("v", 21));
        assertThat(invalidFields(over)).containsExactlyInAnyOrder(
                "name", "username", "password", "url", "tags", "note", "category", "visibility");
    }

    @Test
    void vaultNullFieldsMeanUnchangedAndPassValidation() {
        assertThat(invalidFields(new VaultItemDTO())).isEmpty();
    }

    @Test
    void loanAmountPrecisionMatchesDecimal14_2Column() {
        FamilyLoanDTO atCeiling = baseLoan();
        atCeiling.setAmount(new BigDecimal("999999999999.99"));
        atCeiling.setFirstPayment(new BigDecimal("0.01"));
        assertThat(invalidFields(atCeiling)).isEmpty();

        FamilyLoanDTO tooManyIntegerDigits = baseLoan();
        tooManyIntegerDigits.setAmount(new BigDecimal("1000000000000"));
        assertThat(invalidFields(tooManyIntegerDigits)).contains("amount");

        FamilyLoanDTO tooManyDecimals = baseLoan();
        tooManyDecimals.setFirstPayment(new BigDecimal("1234.567"));
        assertThat(invalidFields(tooManyDecimals)).contains("firstPayment");
    }

    @Test
    void loanEventAmountPrecisionCascades() {
        FamilyLoanDTO dto = baseLoan();
        FamilyLoanDTO.EventDTO event = new FamilyLoanDTO.EventDTO();
        event.setType("PREPAY");
        event.setEffectivePeriod(12);
        event.setAmount(new BigDecimal("1.234"));
        dto.setEvents(List.of(event));
        assertThat(invalidFields(dto)).contains("events[0].amount");
    }

    @Test
    void loanNameAndNoteBoundedByColumnWidth() {
        FamilyLoanDTO dto = baseLoan();
        dto.setName(repeat("贷", 101));
        dto.setGroupName(repeat("组", 101));
        dto.setNote(repeat("备", 501));
        assertThat(invalidFields(dto)).containsExactlyInAnyOrder("name", "groupName", "note");

        FamilyLoanDTO.EventDTO event = new FamilyLoanDTO.EventDTO();
        event.setType("RATE_CHANGE");
        event.setEffectivePeriod(12);
        event.setRate(new BigDecimal("4.9"));
        event.setNote(repeat("备", 501));
        dto.setEvents(List.of(event));
        assertThat(invalidFields(dto)).contains("events[0].note");
    }

    /** 满足业务必填的最小表单(@Digits/@Size 只拦形状,必填与范围仍由 Service 校验) */
    private FamilyLoanDTO baseLoan() {
        FamilyLoanDTO dto = new FamilyLoanDTO();
        dto.setName("商贷");
        dto.setAmount(new BigDecimal("100000"));
        dto.setMonths(120);
        dto.setRate(new BigDecimal("3.1"));
        return dto;
    }

    @Test
    void bookAccountFieldsBoundedByColumnWidth() {
        BookAccountDTO atCeiling = new BookAccountDTO();
        atCeiling.setName(repeat("卡", 50));
        atCeiling.setInstitution(repeat("行", 60));
        atCeiling.setCardTail(repeat("8", 10));
        atCeiling.setNote(repeat("备", 200));
        atCeiling.setInitialBalance(new BigDecimal("999999999999.99"));
        assertThat(invalidFields(atCeiling)).isEmpty();

        BookAccountDTO over = new BookAccountDTO();
        over.setName(repeat("卡", 51));
        over.setInstitution(repeat("行", 61));
        over.setCardTail(repeat("8", 11));
        over.setNote(repeat("备", 201));
        over.setInitialBalance(new BigDecimal("1000000000000"));
        assertThat(invalidFields(over)).containsExactlyInAnyOrder(
                "name", "institution", "cardTail", "note", "initialBalance");

        BookAccountDTO tooManyDecimals = new BookAccountDTO();
        tooManyDecimals.setName("零钱");
        tooManyDecimals.setInitialBalance(new BigDecimal("1.234"));
        assertThat(invalidFields(tooManyDecimals)).contains("initialBalance");
    }

    @Test
    void bookAccountNameRequiredAndTypeEnumerated() {
        BookAccountDTO blankName = new BookAccountDTO();
        blankName.setName("  ");
        assertThat(invalidFields(blankName)).contains("name");

        // 空值/空串交给 Service 归一成银行卡,不在校验层拦截
        BookAccountDTO emptyType = new BookAccountDTO();
        emptyType.setName("现金");
        emptyType.setAccountType("");
        assertThat(invalidFields(emptyType)).isEmpty();
        assertThat(invalidFields(new BookAccountDTO())).doesNotContain("accountType");

        for (String ok : List.of("CASH", "BANK", "ALIPAY", "WECHAT", "CREDIT", "OTHER", "bank")) {
            BookAccountDTO dto = new BookAccountDTO();
            dto.setName("卡");
            dto.setAccountType(ok);
            assertThat(invalidFields(dto)).as(ok).isEmpty();
        }

        for (String bad : List.of("0", "1", "现金", "CASHX", "BANK_CARD")) {
            BookAccountDTO dto = new BookAccountDTO();
            dto.setName("卡");
            dto.setAccountType(bad);
            assertThat(invalidFields(dto)).as(bad).containsExactly("accountType");
        }
    }
}
