// Copyright (c) 2020 ChromaWay AB. See README for license information.

//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2022.04.22 at 11:21:27 AM EEST 
//


package net.postchain.base.gtxml;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElementRefs;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for signersType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="signersType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;choice maxOccurs="unbounded" minOccurs="0">
 *         &lt;element ref="{}bytea"/>
 *         &lt;element ref="{}param"/>
 *       &lt;/choice>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "signersType", propOrder = {
    "signers"
})
public class SignersType {

    @XmlElementRefs({
        @XmlElementRef(name = "param", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "bytea", type = JAXBElement.class, required = false)
    })
    protected List<JAXBElement<?>> signers;

    /**
     * Gets the value of the signers property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the signers property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSigners().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link JAXBElement }{@code <}{@link byte[]}{@code >}
     * {@link JAXBElement }{@code <}{@link ParamType }{@code >}
     * 
     * 
     */
    public List<JAXBElement<?>> getSigners() {
        if (signers == null) {
            signers = new ArrayList<JAXBElement<?>>();
        }
        return this.signers;
    }

}
