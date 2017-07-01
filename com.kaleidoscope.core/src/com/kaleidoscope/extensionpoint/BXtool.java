package com.kaleidoscope.extensionpoint;

import java.nio.file.Path;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;

public interface BXtool {
	public EObject getSourceModel();
	public EObject getTargetModel();
	
	public void setSourceModel(EObject sourceModel);
	public void setTargetModel(EObject targetModel);

	public void sourceToTargetTransformation();
	public void targetToSourceTransformation();
	
	public void setWorkingDirectory(Path workingDirectory);
	public Path getWorkingDirectory();
	
	public void setResourceSet(ResourceSet resourceSet);
	
	public void syncForwardFromDelta(Path absPathToDeltaSpec);
	public void syncBackwardFromDelta(Path absPathToDeltaSpec);
	
	public void persistModels();
}
