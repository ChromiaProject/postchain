/*
 * Generated by ASN.1 Java Compiler (http://www.asnlab.org/)
 * From ASN.1 module "Messages"
 */
package com.chromaway.postchain.gtx.messages;

import java.io.*;
import java.util.*;
import org.asnlab.asndt.runtime.conv.*;
import org.asnlab.asndt.runtime.conv.annotation.*;
import org.asnlab.asndt.runtime.type.AsnType;
import org.asnlab.asndt.runtime.value.*;

public class GTXTransaction {

	@Component(0)
	public Vector<GTXOperation> operations;

	@Component(1)
	public Vector<byte[]> signers;

	@Component(2)
	public Vector<byte[]> signatures;


	public boolean equals(Object obj) {
		if(!(obj instanceof GTXTransaction)){
			return false;
		}
		return TYPE.equals(this, obj, CONV);
	}

	public void der_encode(OutputStream out) throws IOException {
		TYPE.encode(this, EncodingRules.DISTINGUISHED_ENCODING_RULES, CONV, out);
	}

	public static GTXTransaction der_decode(InputStream in) throws IOException {
		return (GTXTransaction)TYPE.decode(in, EncodingRules.DISTINGUISHED_ENCODING_RULES, CONV);
	}


	public final static AsnType TYPE = Messages.type(65540);

	public final static CompositeConverter CONV;

	static {
		CONV = new AnnotationCompositeConverter(GTXTransaction.class);
		AsnConverter operationsConverter = new VectorConverter(GTXOperation.CONV);
		AsnConverter signersConverter = new VectorConverter(OctetStringConverter.INSTANCE);
		AsnConverter signaturesConverter = new VectorConverter(OctetStringConverter.INSTANCE);
		CONV.setComponentConverters(new AsnConverter[] { operationsConverter, signersConverter, signaturesConverter });
	}


}
