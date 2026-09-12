import SwiftUI

struct HomeScreen: View {
    @State private var searchText = ""
    @State private var showNewNotebookDialog = false
    @State private var selectedTemplate = "Lined"
    @State private var newNotebookTitle = ""
    
    // Notebook Data Model
    struct Notebook: Identifiable {
        let id = UUID()
        let title: String
        let coverColor: Color
        let lastModified: String
    }
    
    @State private var notebooks = [
        Notebook(title: "My Journal", coverColor: .indigo, lastModified: "Today"),
        Notebook(title: "Meeting Notes", coverColor: .teal, lastModified: "Yesterday")
    ]
    
    // Custom Colors
    let bgColor = Color(red: 12/255, green: 12/255, blue: 18/255)
    let rubyRed = Color(red: 239/255, green: 68/255, blue: 68/255)
    
    var body: some View {
        NavigationSplitView {
            // Sidebar
            List {
                Section(header: Text("Qazar Notes").foregroundColor(.gray)) {
                    NavigationLink(destination: Text("All Notebooks")) {
                        Label("Bookshelf", systemImage: "books.vertical.fill")
                    }
                    NavigationLink(destination: Text("Folders")) {
                        Label("Folders", systemImage: "folder")
                    }
                }
                Section(header: Text("Settings").foregroundColor(.gray)) {
                    NavigationLink(destination: Text("Settings")) {
                        Label("Settings", systemImage: "gear")
                    }
                }
            }
            .navigationTitle("Qazar Note Book")
            .listStyle(SidebarListStyle())
            
        } detail: {
            ZStack {
                bgColor.ignoresSafeArea()
                
                ScrollView {
                    VStack(alignment: .leading, spacing: 32) {
                        
                        // Header
                        HStack {
                            Text("Your Digital Bookshelf")
                                .font(.system(size: 42, weight: .bold))
                                .foregroundColor(.white)
                            Spacer()
                            
                            Button(action: {
                                showNewNotebookDialog = true
                            }) {
                                HStack {
                                    Image(systemName: "plus")
                                    Text("New Notebook")
                                }
                                .font(.headline)
                                .foregroundColor(.white)
                                .padding(.horizontal, 20)
                                .padding(.vertical, 12)
                                .background(rubyRed)
                                .cornerRadius(12)
                            }
                        }
                        .padding(.horizontal, 32)
                        .padding(.top, 24)
                        
                        // Notebook Grid (Bookshelf)
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 180), spacing: 32)], spacing: 32) {
                            ForEach(notebooks) { notebook in
                                NavigationLink(destination: PdfViewerScreen(documentTitle: notebook.title, template: "Lined")) {
                                    NotebookCover(notebook: notebook)
                                }
                                .buttonStyle(PlainButtonStyle())
                            }
                        }
                        .padding(.horizontal, 32)
                    }
                }
            }
            .sheet(isPresented: $showNewNotebookDialog) {
                NewNotebookModal(
                    title: $newNotebookTitle,
                    selectedTemplate: $selectedTemplate,
                    onCreate: {
                        let newNote = Notebook(title: newNotebookTitle.isEmpty ? "Untitled Note" : newNotebookTitle, coverColor: rubyRed, lastModified: "Just now")
                        notebooks.insert(newNote, at: 0)
                        newNotebookTitle = ""
                        showNewNotebookDialog = false
                    }
                )
            }
        }
    }
}

struct NotebookCover: View {
    let notebook: HomeScreen.Notebook
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Book Cover Design
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 12)
                    .fill(notebook.coverColor)
                    .aspectRatio(3/4, contentMode: .fit)
                    .shadow(color: .black.opacity(0.3), radius: 10, x: 5, y: 5)
                
                // Binder binding effect
                Rectangle()
                    .fill(Color.black.opacity(0.15))
                    .frame(width: 24)
                    .padding(.leading, 8)
                
                // Label
                VStack {
                    Spacer()
                    Text(notebook.title)
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .padding()
                        .background(Color.black.opacity(0.4))
                        .cornerRadius(8)
                        .padding()
                }
            }
            
            // Metadata
            VStack(alignment: .leading, spacing: 4) {
                Text(notebook.title)
                    .font(.headline)
                    .foregroundColor(.white)
                Text(notebook.lastModified)
                    .font(.caption)
                    .foregroundColor(.gray)
            }
            .padding(.leading, 4)
        }
    }
}

struct NewNotebookModal: View {
    @Binding var title: String
    @Binding var selectedTemplate: String
    var onCreate: () -> Void
    
    @Environment(\.presentationMode) var presentationMode
    
    let templates = ["Lined", "Grid", "Dotted", "Blank"]
    
    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Notebook Details")) {
                    TextField("Notebook Title", text: $title)
                }
                
                Section(header: Text("Paper Template")) {
                    Picker("Template", selection: $selectedTemplate) {
                        ForEach(templates, id: \.self) {
                            Text($0)
                        }
                    }
                    .pickerStyle(SegmentedPickerStyle())
                }
                
                Button(action: onCreate) {
                    Text("Create Notebook")
                        .frame(maxWidth: .infinity)
                        .foregroundColor(.white)
                        .padding()
                        .background(Color.red)
                        .cornerRadius(8)
                }
            }
            .navigationTitle("New Notebook")
            .navigationBarItems(trailing: Button("Cancel") {
                presentationMode.wrappedValue.dismiss()
            })
        }
    }
}
