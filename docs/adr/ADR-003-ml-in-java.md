# ADR-003: Implement ML Algorithms in Java (Not Python)

**Date:** 2024-02-01
**Status:** ACCEPTED
**Deciders:** Platform Team
**Technical Domain:** ML/AI Implementation Language

---

## Context and Problem Statement

ConceptualWare must demonstrate 50+ ML/AI concepts (Category 30). The natural choice for
ML implementations is Python (NumPy, scikit-learn, PyTorch). However, the platform's
primary language is Java, and using a Python subprocess adds:
- Service complexity (Python runtime management, subprocess overhead)
- Dependency management across two language ecosystems
- Latency from inter-process communication
- Security surface area (subprocess injection, arbitrary code execution risk)

## Decision Drivers

* Minimize operational complexity (one runtime, one Docker image)
* Educational purity: show HOW algorithms work, not just call library functions
* Security: avoid subprocess execution for user-triggered code
* Demonstrating Java's capability for ML (underappreciated)

## Considered Options

**Option A: Python subprocess (scikit-learn, NumPy)**
- Best library support; industry standard for ML
- Extra service; ~100ms startup overhead per call; security concerns

**Option B: Pure Java from scratch**
- Full control over implementation; readable code showing math directly
- More code; slower development; limited ecosystem vs NumPy

**Option C: Java with DJL (Deep Java Library) or Weka**
- Library support within JVM; bridges to PyTorch/TensorFlow natively
- Additional large dependency; hides algorithm internals

## Decision

**Option B: Pure Java from scratch**

Rationale:
1. The goal is EDUCATION — showing the algorithm math, not using ML in production
2. All algorithms (Linear Regression, K-Means, Neural Network, LSTM, etc.) are implemented
   with visible equations and step-by-step comments
3. `Matrix.java` provides the linear algebra substrate (multiplication, transpose, etc.)
4. No Python runtime required — runs as part of the existing Spring Boot application

## Implementation Notes

```
backend/src/main/java/com/conceptualware/core/ml/
├── Matrix.java              ← Linear algebra (matmul, transpose, standardize)
├── SupervisedLearning.java  ← LinearRegression, LogisticRegression, KNN, NaiveBayes,
│                               DecisionTree, RandomForest
├── UnsupervisedLearning.java ← KMeans (+elbow, silhouette), DBSCAN, PCA, Hierarchical
├── NeuralNetwork.java        ← MLP + Adam, Conv2D, MaxPool, LSTM, GRU
├── TransformerAttention.java ← Self-attention, Multi-head attention, Positional encoding,
│                               BPE Tokenizer, RAG vector store
└── ModelEvaluator.java       ← Accuracy, F1, ROC-AUC, confusion matrix, k-fold CV,
                                Feature Store, PSI drift detection
```

## Consequences

**Positive:**
- All 50+ ML concepts visible in clean Java code with educational comments
- Zero additional infrastructure (no Python service, no model server)
- Tested with JUnit — same testing framework as all other concepts

**Negative:**
- Performance: Java ML inference is slower than NumPy-optimized C code
  (acceptable for demo purposes — not production ML serving)
- Missing advanced algorithms: GBM (XGBoost) implemented conceptually with
  comments; full implementation would require significant matrix optimization

**Mitigation:**
- Matrix operations use cache-friendly row-major storage
- Small dataset sizes (100-1000 samples) keep execution under 2 seconds
- XGBoost concept documented with detailed gradient boosting explanation
