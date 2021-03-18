Processor testDriverProcessor = new Processor(false);

ExtensionFunction converter = new ExtensionFunction() {
    @Override
    public QName getName() {
        return new QName(bindingNameSpace, "convert");
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[]{
            SequenceType.makeSequenceType(
                ItemType.STRING, OccurrenceIndicator.ONE
        )};
    }

    @Override
    public XdmValue call(XdmValue[] arguments) throws SaxonApiException {
        String arg = arguments[0].itemAt(0).getStringValue();
        String result = Convert(arg);
        return new XdmAtomicValue(result);
    }

    @Override
    public SequenceType getResultType() {
        return SequenceType.makeSequenceType(
            ItemType.STRING, OccurrenceIndicator.ONE
        );
    }
};

testDriverProcessor.registerExtensionFunction(converter);
XQueryCompiler xqc = testDriverProcessor.newXQueryCompiler();
xqc.declareNamespace("bf", bindingNameSpace);
XQueryExecutable xqe = xqc.compile(
    "declare function local:transform($nodes as node()*) as node()*{\n" +
        "for $n in $nodes return\n" +
            "typeswitch ($n)\n" +
                "case element (test) " +
                    "return <test>{bf:convert($n/string())}</test>\n" +
                "case element (result) " +
                    "return <result>{bf:convert($n/string())}</result>\n" +
                "case element () " +
                    "return element { fn:node-name($n) } " +
                                   "{$n/@*, local:transform($n/node())} \n" +
                "default " +
                    "return $n\n" +
    "};" +
    "declare variable $test-set external;\n" +
    "let $y := $test-set//test-set\n" +
    "return local:transform($y)");
XQueryEvaluator xQueryEvaluator = xqe.load();
xQueryEvaluator.setExternalVariable(new QName("test-set"), testSetDocNode);
xQueryEvaluator.iterator();