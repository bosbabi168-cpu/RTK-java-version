DakHoNpc = {
	click = async(function(player, npc)
		local name = "<b>[" .. npc.name .. "]\n\n"
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {
			"Tugas Chu Rua",
			"Aku di mana!?",
			"Yellow scroll",
			"Tinggikan dan rendahkan suaramu!"
		}
		local txt = ""

		local menu = player:menuString("Halo! Ada yang bisa kubantu hari ini?", opts)

		if menu == "Tugas Chu Rua" then
			if (player:hasLegend("aided_chu_rua")) then
				player:dialogSeq({t, "Terima kasih sudah menolong Chu Rua!"}, 0)
			end
		elseif menu == "Aku di mana!?" then
			player:dialogSeq(
				{
					t,
					"Kau berada di Tangun.\n\nTangun adalah kota untuk yang muda. Ada gua dan tugas yang cocok bagi pemain yang belum berpengalaman, dan kau bisa belajar banyak hal seperti subjalur, kerajinan, dan penyiapan makanan.",
					"Bosan dengan IronHeart atau JadeSpear?\n\nDi Tangun kau bisa memperoleh lebih banyak barang dan pengetahuan. Tapi jangan lupa kembali menemui tutormu nanti."
				},
				0
			)
		elseif menu == "Yellow scroll" then
			player:dialogSeq(
				{
					t,
					"Memakai gulungan kuning akan membawamu ke penginapan negeri asalmu.\n\nKau bisa membelinya di penginapan dengan harga yang sangat pantas.",
					"Ini beberapa gulungan kuning untuk kau coba."
				},
				1
			)
			player:addItem("yellow_scroll", 5)
		elseif menu == "Tinggikan dan rendahkan suaramu!" then
			player:dialogSeq(
				{
					t,
					"Hei kau! Astaga, jangan kaget begitu! Aku cuma berteriak kepadamu. Di Kingdom of the Winds ini kau bisa meninggikan atau merendahkan nada suaramu.",
					"Kau tidak ingat caranya? Oh, biar kubantu. Kau sudah tahu cara bicara dengan nada biasa, jadi itu awal yang bagus.",
					"Sekarang mari berlatih berteriak. ((Tekan ! atau shift 1 lalu ketik yang ingin kau katakan.)) Kalau kau berteriak, pemain lain bisa mendengarmu dari jarak lebih jauh.",
					"Sebaliknya, kau juga bisa membisikkan sesuatu lewat Angin Kerajaan! Angin bersihir itu membawa suaramu kepada satu orang atau sekelompok orang tertentu. Hanya mereka yang bisa mendengar ucapanmu.",
					"((Tekan shift ' (apostrof) lalu ketik nama orang yang ingin kau ajak bicara dan tekan enter. Setelah itu kau tinggal mengetik pesanmu.))",
					"Kalau kau ingin bicara hanya dengan anggota grup yang kau bentuk, itu juga mudah! ((Ketik !! lalu enter, kemudian ketik pesan yang ingin didengar grupmu.))",
					"Begitu kau masuk Klan atau Subjalur, kau juga bisa berbicara dengan kawan-kawanmu. Kau bisa mempelajarinya dari Sshijok, tepat di dalam balai Klan. *Menunjuk ke pintu*",
					"Nah, andai aku ingat kenapa tadi aku memanggilmu ke sini... hmmm... *ia lupa kau masih berdiri di situ selagi pikirannya mengembara*."
				},
				0
			)
		end
	end)
}
