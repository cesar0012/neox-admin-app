package com.example.ui.screens

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DocumentItem
import com.example.data.rag.RAGQueryResult
import com.example.ui.MainViewModel
import com.example.ui.theme.OnCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.VioletAccent

@Composable
fun KnowledgeRagScreen(viewModel: MainViewModel) {
    val documents by viewModel.allDocuments.collectAsStateWithLifecycle()
    val jobs by viewModel.allJobs.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<RAGQueryResult>>(emptyList()) }
    var showAddDocDialog by remember { mutableStateOf(false) }
    var docToEdit by remember { mutableStateOf<DocumentItem?>(null) }
    var docToDelete by remember { mutableStateOf<DocumentItem?>(null) }

    // Memory filters
    var selectedJobFilter by remember { mutableStateOf("Todos") }
    var selectedCategoryFilter by remember { mutableStateOf("Todas") }
    var sortDescending by remember { mutableStateOf(true) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            searchResults = viewModel.ragEngine.queryMemory(searchQuery, topK = 5)
        } else {
            searchResults = emptyList()
        }
    }

    val filteredDocuments = documents.filter { doc ->
        val matchesJob = selectedJobFilter == "Todos" || doc.jobTag.equals(selectedJobFilter, ignoreCase = true)
        val matchesCat = selectedCategoryFilter == "Todas" || doc.category.equals(selectedCategoryFilter, ignoreCase = true)
        matchesJob && matchesCat
    }.sortedWith { a, b ->
        if (sortDescending) b.createdAt.compareTo(a.createdAt) else a.createdAt.compareTo(b.createdAt)
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // RAG Memory Statistics Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate700))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = CyanNeon)
                            Text("Memoria del Asistente", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Tu memoria local fragmenta y clasifica tus documentos, minutas y directivas. Al consultar, el asistente rescata exactamente la información y citas relevantes al instante.",
                            color = Slate400,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // Real-time Search bar (single line, no long placeholder)
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar en la memoria...", color = Slate400, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyanNeon) },
                    singleLine = true,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().testTag("rag_search_field"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = Slate700,
                        focusedContainerColor = Slate900,
                        unfocusedContainerColor = Slate900
                    )
                )
            }

            // Search results if querying
            if (searchQuery.isNotBlank()) {
                item {
                    Text(
                        text = "Registros y Citas Encontradas (${searchResults.size})",
                        color = CyanNeon,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (searchResults.isEmpty()) {
                    item {
                        Text("No se encontraron coincidencias vectoriales para esta búsqueda.", color = Slate400, fontSize = 12.sp)
                    }
                } else {
                    items(searchResults) { result ->
                        RagChunkCard(result = result)
                    }
                }
            }

            // Document library header + filters
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Directivas y Delineamientos (${filteredDocuments.size})",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        // Sort toggle button
                        TextButton(
                            onClick = { sortDescending = !sortDescending },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (sortDescending) "Más recientes" else "Más antiguas",
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Compact Filters Row for Memory
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp))
                        Text("Filtros:", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                label = "Todos los Proyectos",
                                isSelected = selectedJobFilter == "Todos",
                                onClick = { selectedJobFilter = "Todos" }
                            )
                        }
                        items(jobs.map { it.name }) { j ->
                            FilterChip(
                                label = j,
                                isSelected = selectedJobFilter == j,
                                onClick = { selectedJobFilter = j }
                            )
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("Todas", "DIRECTIVA", "ESPECIFICACION", "REPORTE")) { cat ->
                            FilterChip(
                                label = if (cat == "Todas") "Todas las Categorías" else cat,
                                isSelected = selectedCategoryFilter == cat,
                                onClick = { selectedCategoryFilter = cat }
                            )
                        }
                    }
                }
            }

            if (filteredDocuments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (documents.isEmpty()) "No tienes directivas ni documentos adjuntos aún. Toca + para agregar."
                            else "No hay documentos que coincidan con los filtros seleccionados.",
                            color = Slate400,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(filteredDocuments, key = { it.id }) { doc ->
                    DocumentCard(
                        doc = doc,
                        onEdit = { docToEdit = doc },
                        onDelete = { docToDelete = doc }
                    )
                }
            }
        }

        // Add Document FAB
        FloatingActionButton(
            onClick = { showAddDocDialog = true },
            containerColor = CyanNeon,
            contentColor = OnCyan,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).testTag("add_doc_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Adjuntar documento")
        }
    }

    if (showAddDocDialog) {
        AddOrEditDocumentDialog(
            doc = null,
            jobList = jobs.map { it.name },
            onDismiss = { showAddDocDialog = false },
            onSave = { title, jobTag, category, content ->
                viewModel.addDocument(title, jobTag, category, content)
                showAddDocDialog = false
            }
        )
    }

    if (docToEdit != null) {
        AddOrEditDocumentDialog(
            doc = docToEdit,
            jobList = jobs.map { it.name },
            onDismiss = { docToEdit = null },
            onSave = { title, jobTag, category, content ->
                viewModel.updateDocument(
                    docToEdit!!.copy(
                        title = title,
                        jobTag = jobTag,
                        category = category,
                        content = content
                    )
                )
                docToEdit = null
            }
        )
    }

    if (docToDelete != null) {
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            containerColor = Slate900,
            title = { Text("Eliminar Directiva", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "¿Deseas eliminar '${docToDelete!!.title}' de la memoria? Esto también borrará los fragmentos indexados en el asistente.",
                    color = Slate400,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument(docToDelete!!)
                        docToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError, contentColor = TextPrimary)
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { docToDelete = null }) {
                    Text("Cancelar", color = Slate400)
                }
            }
        )
    }
}

@Composable
fun RagChunkCard(result: RAGQueryResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CyanNeon.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(result.chunk.sourceType, color = CyanNeon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(result.chunk.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("Score: ${String.format(Locale.ROOT, "%.2f", result.score)}", color = EmeraldSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text("« ${result.chunk.exactQuote} »", color = com.example.ui.theme.CyanAccent, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)

            Spacer(modifier = Modifier.height(4.dp))

            Text("Proyecto: ${result.chunk.jobTag} | Fecha: ${result.chunk.dateString}", color = Slate400, fontSize = 10.sp)
        }
    }
}

@Composable
fun DocumentCard(
    doc: DocumentItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(doc.createdAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(VioletAccent.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(doc.category, color = VioletAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(doc.jobTag, color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar directiva", tint = CyanNeon, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar directiva", tint = Slate400, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(doc.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(4.dp))

            Text(doc.content, color = Slate400, fontSize = 12.sp, maxLines = 4, lineHeight = 17.sp)

            Spacer(modifier = Modifier.height(8.dp))

            Text("Guardado el: $dateStr", color = Slate700, fontSize = 10.sp)
        }
    }
}

@Composable
fun AddOrEditDocumentDialog(
    doc: DocumentItem? = null,
    jobList: List<String>,
    onDismiss: () -> Unit,
    onSave: (title: String, jobTag: String, category: String, content: String) -> Unit
) {
    val isEditing = doc != null
    var title by remember { mutableStateOf(doc?.title ?: "") }
    var selectedJob by remember { mutableStateOf(doc?.jobTag ?: if (jobList.isNotEmpty()) jobList.first() else "General") }
    var selectedCategory by remember { mutableStateOf(doc?.category ?: "DIRECTIVA") }
    var content by remember { mutableStateOf(doc?.content ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        title = {
            Text(
                if (isEditing) "Editar Directiva / Documento" else "Adjuntar Documento / Delineamiento",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título", color = Slate400) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = Slate700
                    )
                )

                Text("Proyecto / Trabajo:", color = Slate400, fontSize = 11.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val opts = if (jobList.isEmpty()) listOf("General", "Trabajo 1") else jobList
                    items(opts) { j ->
                        FilterChip(label = j, isSelected = selectedJob == j, onClick = { selectedJob = j })
                    }
                }

                Text("Categoría:", color = Slate400, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("DIRECTIVA", "ESPECIFICACION", "REPORTE").forEach { cat ->
                        FilterChip(label = cat, isSelected = selectedCategory == cat, onClick = { selectedCategory = cat })
                    }
                }

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Contenido o directivas de trabajo", color = Slate400) },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = Slate700
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onSave(title, selectedJob, selectedCategory, content)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = OnCyan)
            ) {
                Text(if (isEditing) "Actualizar e Indexar" else "Guardar e Indexar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Slate400) }
        }
    )
}
