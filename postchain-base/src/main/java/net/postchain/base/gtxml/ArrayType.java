// Copyright (c) 2020 ChromaWay AB. See README for license information.

//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2022.04.22 at 09:02:55 AM EEST 
//


package net.postchain.base.gtxml;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElementRefs;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for arrayType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="arrayType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;choice maxOccurs="unbounded" minOccurs="0">
 *         &lt;element ref="{}null"/>
 *         &lt;element ref="{}string"/>
 *         &lt;element ref="{}int"/>
 *         &lt;element ref="{}long"/>
 *         &lt;element ref="{}bigint"/>
 *         &lt;element ref="{}bytea"/>
 *         &lt;element ref="{}array"/>
 *         &lt;element ref="{}dict"/>
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
@XmlType(name = "arrayType", propOrder = {
    "elements"
})
public class ArrayType {

    @XmlElementRefs({
        @XmlElementRef(name = "bigint", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "long", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "array", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "param", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "null", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "int", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "dict", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "string", type = JAXBElement.class, required = false),
        @XmlElementRef(name = "bytea", type = JAXBElement.class, required = false)
    })
    protected List<JAXBElement<?>> elements;

    /**
     * Gets the value of the elements property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the elements property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getElements().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link JAXBElement }{@code <}{@link BigInteger }{@code >}
     * {@link JAXBElement }{@code <}{@link Long }{@code >}
     * {@link JAXBElement }{@code <}{@link ArrayType }{@code >}
     * {@link JAXBElement }{@code <}{@link ParamType }{@code >}
     * {@link JAXBElement }{@code <}{@link Object }{@code >}
     * {@link JAXBElement }{@code <}{@link Integer }{@code >}
     * {@link JAXBElement }{@code <}{@link DictType }{@code >}
     * {@link JAXBElement }{@code <}{@link String }{@code >}
     * {@link JAXBElement }{@code <}{@link byte[]}{@code >}
     * 
     * 
     */
    public List<JAXBElement<?>> getElements() {
        if (elements == null) {
            elements = new ArrayList<JAXBElement<?>>();
        }
        return this.elements;
    }

}
