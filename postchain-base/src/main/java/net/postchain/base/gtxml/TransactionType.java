//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2018.07.17 at 01:04:12 PM EEST 
//


package net.postchain.base.gtxml;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for transactionType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="transactionType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="signers" type="{}signersType" minOccurs="0"/>
 *         &lt;element name="operations" type="{}operationsType" minOccurs="0"/>
 *         &lt;element name="signatures" type="{}signaturesType" minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;attribute name="blockchainRID" type="{http://www.w3.org/2001/XMLSchema}anySimpleType" />
 *       &lt;attribute name="failure" type="{http://www.w3.org/2001/XMLSchema}boolean" default="false" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "transactionType", propOrder = {
    "signers",
    "operations",
    "signatures"
})
public class TransactionType {

    protected SignersType signers;
    protected OperationsType operations;
    protected SignaturesType signatures;
    @XmlAttribute(name = "blockchainRID")
    @XmlSchemaType(name = "anySimpleType")
    protected String blockchainRID;
    @XmlAttribute(name = "failure")
    protected Boolean failure;

    /**
     * Gets the value of the signers property.
     * 
     * @return
     *     possible object is
     *     {@link SignersType }
     *     
     */
    public SignersType getSigners() {
        return signers;
    }

    /**
     * Sets the value of the signers property.
     * 
     * @param value
     *     allowed object is
     *     {@link SignersType }
     *     
     */
    public void setSigners(SignersType value) {
        this.signers = value;
    }

    /**
     * Gets the value of the operations property.
     * 
     * @return
     *     possible object is
     *     {@link OperationsType }
     *     
     */
    public OperationsType getOperations() {
        return operations;
    }

    /**
     * Sets the value of the operations property.
     * 
     * @param value
     *     allowed object is
     *     {@link OperationsType }
     *     
     */
    public void setOperations(OperationsType value) {
        this.operations = value;
    }

    /**
     * Gets the value of the signatures property.
     * 
     * @return
     *     possible object is
     *     {@link SignaturesType }
     *     
     */
    public SignaturesType getSignatures() {
        return signatures;
    }

    /**
     * Sets the value of the signatures property.
     * 
     * @param value
     *     allowed object is
     *     {@link SignaturesType }
     *     
     */
    public void setSignatures(SignaturesType value) {
        this.signatures = value;
    }

    /**
     * Gets the value of the blockchainRID property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getBlockchainRID() {
        return blockchainRID;
    }

    /**
     * Sets the value of the blockchainRID property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setBlockchainRID(String value) {
        this.blockchainRID = value;
    }

    /**
     * Gets the value of the failure property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public boolean isFailure() {
        if (failure == null) {
            return false;
        } else {
            return failure;
        }
    }

    /**
     * Sets the value of the failure property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setFailure(Boolean value) {
        this.failure = value;
    }

}
