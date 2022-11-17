# Postchain-gtv

## GTV
GTV is a general purpose protocol, used to express data in 
- Primitive types (integers, strings, byte arrays, etc)
- Complex types (only Array and Dictionary)

## ASN.1 as underlying protocol
GTV is usually converted to ASN.1 when it's transported over the wire, and this is what we mean when 
we say "binary" format.

## XML and JSON
Sometimes we use XML or JSON to express GTV, and we have code to translate between these format to GTV kotlin types
and back. For XML we use "gtvml" classes.
