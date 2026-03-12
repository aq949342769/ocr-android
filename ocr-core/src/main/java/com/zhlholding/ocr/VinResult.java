package com.zhlholding.ocr;

/**
 * VIN码识别结果
 */
public class VinResult {
    /**
     * 识别状态
     */
    public enum Status {
        SUCCESS,        // 识别成功
        NOT_RECOGNIZED // 未识别到
    }

    /**
     * 识别的VIN码（标准化后）
     */
    private String vinCode;

    /**
     * 原始识别结果（未标准化）
     */
    private String rawResult;

    /**
     * 识别状态
     */
    private Status status;

    /**
     * 附加信息
     */
    private String extraMessage;

    public VinResult() {
    }

    public VinResult(String vinCode, Status status, String extraMessage) {
        this.vinCode = vinCode;
        this.status = status;
        this.extraMessage = extraMessage;
        this.rawResult = vinCode;
    }

    public String getVinCode() {
        return vinCode;
    }

    public void setVinCode(String vinCode) {
        this.vinCode = vinCode;
    }

    public String getRawResult() {
        return rawResult;
    }

    public void setRawResult(String rawResult) {
        this.rawResult = rawResult;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getExtraMessage() {
        return extraMessage;
    }

    public void setExtraMessage(String extraMessage) {
        this.extraMessage = extraMessage;
    }

    /**
     * 是否识别成功
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS && vinCode != null && !vinCode.isEmpty();
    }

    @Override
    public String toString() {
        return "VinResult{" +
                "vinCode='" + vinCode + '\'' +
                ", rawResult='" + rawResult + '\'' +
                ", status=" + status +
                ", extraMessage='" + extraMessage + '\'' +
                '}';
    }
}
