package com.kaleidoscope.core.auxiliary.simplejava.artefactadapter;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import org.eclipse.core.runtime.IProgressMonitor;

import com.kaleidoscope.core.auxiliary.simplejava.artefactadapter.normaliser.JavaPackageToString;
import com.kaleidoscope.extensionpoint.ArtefactAdapter;

import SimpleJava.SimpleJavaFactory;
import SimpleJava.JavaArrayInit;
import SimpleJava.JavaAssignment;
import SimpleJava.JavaCompilationUnit;
import SimpleJava.JavaExpression;
import SimpleJava.JavaImport;
import SimpleJava.JavaLiteral;
import SimpleJava.JavaMethod;
import SimpleJava.JavaMethodInvocation;
import SimpleJava.JavaName;
import SimpleJava.JavaOpaqueMethod;
import SimpleJava.JavaPackage;
import SimpleJava.JavaStatement;
import SimpleJava.JavaUnknownStatement;
import SimpleJava.JavaVariableDeclaration;
import SimpleJava.JavaWorkflowMethod;

public class JavaArtefactAdapter implements ArtefactAdapter {
	
	private List<Path> javaFilePaths;
	private Path packageAbsPath;
	
	
	//private IProgressMonitor monitor;
	private static final Logger logger = Logger.getLogger(JavaArtefactAdapter.class);
	
	@Override
	public EObject parse(Object parseSource){
		
		try{
			
			javaFilePaths = (List<Path>)parseSource;
			JavaPackage pack = SimpleJavaFactory.eINSTANCE.createJavaPackage();
			for (Path filePath : javaFilePaths) {
				 parseJavaFile(pack, filePath);
			}
			return pack;
		
		}catch (ClassCastException | NullPointerException e) {
			return null;
		}
	}
	public JavaCompilationUnit parseJavaFile(JavaPackage pack, Path absJavaFilePath){
		logger.info("Parsing " + absJavaFilePath + " into a java model!");		
		String fieldDeclarations = "";
		JavaCompilationUnit jcu = SimpleJavaFactory.eINSTANCE.createJavaCompilationUnit();
		Scanner scanner = null;
		
		try {
			scanner = new Scanner(absJavaFilePath.toFile(),"UTF-8").useDelimiter("\\A");
		} catch (FileNotFoundException e) {
			logger.error("Unable to load java file that needs to be parsed into a java model!", e);
		}
		String fileContent = scanner.hasNext() ? scanner.next() : "";
		scanner.close();
		
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(false);
		parser.setBindingsRecovery(false);
		parser.setSource(fileContent.toCharArray());
		
		// get JDT compilation unit
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		
		// set jcu as compilation unit of packege and set the package name
		pack.getCunits().add(jcu);
		// package name
		pack.setName(cu.getPackage().getName().toString());
	
		
		// set java compilation unit name
		TypeDeclaration classTypeDec = (TypeDeclaration) cu.types().get(0); //typeDec is the class  
		SimpleName classTypeName = classTypeDec.getName();
		jcu.setName(classTypeName.getIdentifier());
		
		// to iterate through methods and imports
	    List<AbstractTypeDeclaration> types = cu.types();
	    List<ImportDeclaration>imports = cu.imports();
	    
	    int importIndex = 0;
	    // add import to java compilation unit
	    for(ImportDeclaration importDec:imports){
	    		 
	    	JavaImport javaImport = SimpleJavaFactory.eINSTANCE.createJavaImport();
	    	javaImport.setIndex(importIndex);
	    	javaImport.setValue(importDec.getName().getFullyQualifiedName());
	    	jcu.getImports().add(javaImport);
	    	
	    	importIndex++;
	    }
	    int methodIndex = 0;
	    // iterate through all the methods
	    for (AbstractTypeDeclaration type : types) {
	    	if(type.getNodeType() == ASTNode.LINE_COMMENT){
	   
	    	}
	    	if(type.getNodeType() == ASTNode.BLOCK_COMMENT){
	  
	    	}
	        if (type.getNodeType() == ASTNode.TYPE_DECLARATION) {	   
	            List<BodyDeclaration> bodies = type.bodyDeclarations();
	            
	            for (BodyDeclaration body : bodies) {
	            	if(body.getNodeType() == ASTNode.FIELD_DECLARATION){	      	            	
	            		FieldDeclaration field = (FieldDeclaration)body;
	            		fieldDeclarations = fieldDeclarations + field.toString();
	            	}
	            	else if (body.getNodeType() == ASTNode.METHOD_DECLARATION) {
	            		
	                    MethodDeclaration method = (MethodDeclaration)body;	                    	        
	                    JavaMethod javaMethod = methodHandler(method);
	                    
	                    String modifiers = (String) method.modifiers().stream().filter(x -> x instanceof Modifier).map(Object::toString).collect(Collectors.joining(" "));
	                    javaMethod.setModifier(modifiers);
	                    
	                    javaMethod.setIndex(methodIndex);
	                    
	                    jcu.getMethods().add(javaMethod);
	                    
	                    logger.info("Method declaration detected with the following name: " + method.getName());
	                    methodIndex++;
	                }
	            }
	        }
	    }
	    jcu.setFieldDeclarations(fieldDeclarations);
	    logger.info("Parsing of a " + absJavaFilePath + "is finished!");
	    return jcu;
	}
	JavaMethod methodHandler(MethodDeclaration method){	       
		
		
        if(!method.modifiers().toString().contains("@Generated(value={\"Crypto\"})")){
        	return opaqueMethodHandler(method);
        }else{
        	return workflowMethodHandler(method);
        }
	}
	JavaMethod opaqueMethodHandler(MethodDeclaration method){
		JavaOpaqueMethod javaMethod = SimpleJavaFactory.eINSTANCE.createJavaOpaqueMethod();
		javaMethod.setName(method.getName().getFullyQualifiedName());
        javaMethod.setType(StringUtils.join(method.getReturnType2().toString(),' '));
        javaMethod.setModifier(StringUtils.join(method.modifiers(), ' '));
        javaMethod.setThrows(StringUtils.join(method.thrownExceptionTypes(),','));
        
        javaMethod.setBody(method.getBody().toString());
        javaMethod.setParameters(StringUtils.join(method.parameters(), ','));
        /*
        int index = 0;
        for(Object varDec : method.parameters()){
        	if(varDec instanceof SingleVariableDeclaration){
        		JavaVariableDeclaration javaVarDec = variableDeclarationHandler((SingleVariableDeclaration)varDec);
        		javaVarDec.setIndex(index);
        		javaMethod.getParams().add(javaVarDec);
        		index++;
        	}	                
        }
        */
        return javaMethod;
	}
	JavaMethod workflowMethodHandler(MethodDeclaration method){
		JavaWorkflowMethod javaMethod = SimpleJavaFactory.eINSTANCE.createJavaWorkflowMethod();
        javaMethod.setName(method.getName().getFullyQualifiedName());
        javaMethod.setType(StringUtils.join(method.getReturnType2().toString(),' '));
        javaMethod.setModifier(StringUtils.join(method.modifiers(), ' '));
        javaMethod.setThrows(StringUtils.join(method.thrownExceptionTypes(),','));
     	 
        int index = 0;
        for(Object varDec : method.parameters()){
        	if(varDec instanceof SingleVariableDeclaration){
        		JavaVariableDeclaration javaVarDec = variableDeclarationHandler((SingleVariableDeclaration)varDec);
        		javaVarDec.setIndex(index);
        		javaMethod.getParams().add(javaVarDec);
        		index++;
        	}	                
        }
        for(Object statement : method.getBody().statements()){
        	javaMethod.getStatements().add(statementHandler((Statement)statement));
        	
        }
        return javaMethod;
	}
	JavaStatement statementHandler(Statement statement){
		
		
		if(statement instanceof ReturnStatement){
			return returnStatementHandler((ReturnStatement)statement);
		}
		else if(statement instanceof ExpressionStatement){
			return expressionStatementHandler((ExpressionStatement)statement);
		}
		else if(statement instanceof VariableDeclarationStatement){
			return variableDeclarationStatementHandler((VariableDeclarationStatement)statement);
		}
		else{
			return unknownStatementHandler(statement);
		}
	}
	JavaStatement unknownStatementHandler(Statement statement){
		JavaUnknownStatement javaStatement = SimpleJavaFactory.eINSTANCE.createJavaUnknownStatement();
		javaStatement.setBody(statement.toString());
		return javaStatement;
	}
	JavaStatement returnStatementHandler(ReturnStatement statement){
		JavaStatement javaStatement = SimpleJavaFactory.eINSTANCE.createJavaStatement();
		javaStatement.setReturn(true);
		javaStatement.setExpr(expressionHandler(statement.getExpression()));
		
		return javaStatement;
	}
	JavaStatement variableDeclarationStatementHandler(VariableDeclarationStatement statement){
		JavaStatement javaStatement = SimpleJavaFactory.eINSTANCE.createJavaStatement();
		JavaAssignment assignment = SimpleJavaFactory.eINSTANCE.createJavaAssignment();
	
		javaStatement.setExpr(assignment);
		javaStatement.setReturn(false);
		
		JavaVariableDeclaration jvd = SimpleJavaFactory.eINSTANCE.createJavaVariableDeclaration();
		jvd.setType(statement.getType().toString());
		VariableDeclarationFragment frag = (VariableDeclarationFragment)statement.fragments().get(0);	            
		jvd.setName(frag.getName().getIdentifier());
		
		assignment.setLhs(jvd);
		assignment.setRhs(expressionHandler(frag.getInitializer()));
		
		return javaStatement;
	}
	JavaStatement expressionStatementHandler(ExpressionStatement statement){
		JavaStatement javaStatement = SimpleJavaFactory.eINSTANCE.createJavaStatement();
		javaStatement.setExpr(expressionHandler(statement.getExpression()));
		
		return javaStatement;
	}
	JavaExpression expressionHandler(Expression expression){
		
		if(expression instanceof MethodInvocation){
			return methodInvocationHandler((MethodInvocation)expression);
		}
		else if(expression instanceof Name){
			return nameHandler((Name)expression);
		}
		else if(expression instanceof Assignment){
			return assignmentHandler((Assignment)expression);
		}
		else if(expression instanceof ClassInstanceCreation){
			return classInstanceCreationHandler((ClassInstanceCreation)expression);
		}
		else if(expression instanceof ArrayCreation){
			return arrayCreationHandler((ArrayCreation)expression);
		}
		else if(expression instanceof StringLiteral){
			return literalHandler(expression);
		}	
		else{
			return literalHandler(expression);
		}
		
	}
	private JavaVariableDeclaration variableDeclarationHandler(SingleVariableDeclaration varDec){
		
	    JavaVariableDeclaration javaVarDec = SimpleJavaFactory.eINSTANCE.createJavaVariableDeclaration();
	    		
	    SingleVariableDeclaration singleVarDec = (SingleVariableDeclaration)varDec;
	    javaVarDec.setType(singleVarDec.getType().toString());
	    javaVarDec.setName(singleVarDec.getName().getIdentifier());
	    		
    	return 	javaVarDec;
	}
	private JavaAssignment assignmentHandler(Assignment assignment){
		JavaAssignment javaAssign = SimpleJavaFactory.eINSTANCE.createJavaAssignment();
		
		javaAssign.setLhs(expressionHandler(assignment.getLeftHandSide()));
		javaAssign.setRhs(expressionHandler(assignment.getRightHandSide()));
		
		return javaAssign;
	}
	private JavaName nameHandler(Name name){
		JavaName javaName = SimpleJavaFactory.eINSTANCE.createJavaName();
		javaName.setIdentifier(name.toString());
		return javaName;
	}
	private JavaLiteral literalHandler(Expression str){
		JavaLiteral javaLiteral = SimpleJavaFactory.eINSTANCE.createJavaLiteral();
		javaLiteral.setValue(str.toString());
		return javaLiteral;
	}
	private JavaArrayInit arrayCreationHandler(ArrayCreation arrayCreation){
		JavaArrayInit arrayInit = SimpleJavaFactory.eINSTANCE.createJavaArrayInit();
		String type = arrayCreation.getType().toString();
		String dimension = arrayCreation.dimensions().get(0).toString();
		
		arrayInit.setDimension(dimension);
		arrayInit.setType(type);
		
		return arrayInit;
	}
	private JavaMethodInvocation classInstanceCreationHandler(ClassInstanceCreation classInstance){
		JavaMethodInvocation constructorInvocation = SimpleJavaFactory.eINSTANCE.createJavaMethodInvocation();
		constructorInvocation.setInitialization(true);
		constructorInvocation.setName(classInstance.getType().toString());
		
		for(Object argument : classInstance.arguments()){
			JavaExpression javaArgument = expressionHandler((Expression)argument);
			constructorInvocation.getArguments().add(javaArgument);
		}
		return constructorInvocation;
	}
	private JavaMethodInvocation methodInvocationHandler(MethodInvocation methodInv){
		
		JavaMethodInvocation javaMethodInv = SimpleJavaFactory.eINSTANCE.createJavaMethodInvocation();
		javaMethodInv.setName(methodInv.getName().getIdentifier());
		
		int index = 0;
		for(Object argument : methodInv.arguments()){
			JavaExpression javaArgument = expressionHandler((Expression)argument);
			javaArgument.setIndex(index);
			javaMethodInv.getArguments().add(javaArgument);
			index++;
		}
		if(methodInv.getExpression() != null){		
			javaMethodInv.setOptionalExpression(expressionHandler(methodInv.getExpression()));
		}
		return javaMethodInv;
	}
	@Override
	public void unparse(Object unparseSource, Object content) {
		
		logger.info("Starting to unparse java model!");
		
		JavaPackageToString gcs = new JavaPackageToString();
		JavaPackage jp = (JavaPackage)content;
		
		for (JavaCompilationUnit jcu : jp.getCunits()) {
			
			String fileContent = gcs.unparseCompilationUnit(jp.getName(),jcu).toString();
			
			try {		
				packageAbsPath = (Path)unparseSource;
				Path javaPath = packageAbsPath.resolve(Paths.get("src", jp.getName().replace('.', File.separatorChar), jcu.getName() + ".java"));
				File javaFile = javaPath.toFile();
				FileUtils.writeStringToFile(javaFile, fileContent);
			} catch (IOException e) {		
				logger.error("There was a problem in executing addAllFoldersAndFile!", e);
			}				
		}
		
	}
	@Override
	public void setResourceSet(ResourceSet resourceSet) {
		
	}
}