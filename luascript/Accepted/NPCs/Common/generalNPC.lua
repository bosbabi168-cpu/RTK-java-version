generalNPC = {
	crafting_skills = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local craftingskills = {
			"Keterangan umum tentang keahlian kerajinan.",
			"Keahlian pengumpulan.",
			"Keahlian pembuatan.",
			"Keahlian pengolahan.",
			"Terima kasih, tidak ada untuk sekarang."
		}

		local choice = player:menuSeq(
			"Dengan senang hati kuceritakan soal keahlian kerajinan. Apa yang ingin kau pelajari?",
			craftingskills,
			{}
		)

		if choice == 1 then
			player:dialogSeq(
				{
					t,
					"Ada tiga jenis keahlian kerajinan: Gathering, Manufacturing, dan Refining. Pada mulanya kau tidak terlatih pada satu pun.",
					"Setiap kali kau berhasil memakai satu keahlian, kemampuanmu pada keahlian itu perlahan meningkat. Peningkatannya terasa lebih cepat selagi tingkat keahlianmu masih rendah.",
					"Makin mahir kau, makin lama waktu yang dibutuhkan untuk meningkat. Menjadi 'Master' atau lebih tinggi memakan waktu sangat lama.",
					"Seiring keahlianmu membaik, kau makin jarang gagal dan makin sering berhasil. Sebagian besar keahlian membutuhkan alat atau bahan.",
					"Di seluruh RTK kau akan menemukan pedagang yang menguasai berbagai keahlian. Tiap pedagang akan menjelaskan rincian cara keahliannya dijalankan."
				},
				1
			)
		elseif choice == 2 then
			player:dialogSeq(
				{
					t,
					"Keahlian Gathering paling mudah diperoleh. Bahkan orang tanpa keahlian pun bisa melakukannya dengan lumayan. Isinya mengumpulkan bahan mentah untuk dijual atau dipakai pada keahlian yang lebih tinggi.",
					"Pada akhirnya semua orang bisa menjadi master pada seluruh keahlian gathering. Keahlian gathering biasanya membutuhkan alat.",
					"Kau harus sedikitnya level 8 untuk mengumpulkan bahan."
				},
				1
			)
		elseif choice == 3 then
			player:dialogSeq(
				{
					t,
					"Keahlian Manufacturing mengubah bahan mentah menjadi bentuk yang lebih berharga. Kau bisa mencapai tingkat 'Accomplished' pada keahlian manufacturing mana pun.",
					"Kau juga bisa mendalami satu keahlian manufacturing tertentu. Dengan kerja yang cukup, kau bisa menjadi 'Master' atau lebih tinggi pada keahlian itu.",
					"Kau akan mendapati bahwa kadang kau tetap gagal pada keahlian manufacturing yang pengalamanmu sudah besar. Namun secara keseluruhan, hasil kerjamu makin baik dan uangmu makin banyak seiring kemajuanmu.",
					"Kau harus sedikitnya level 25 untuk menjalankan keahlian manufacturing."
				},
				1
			)
		elseif choice == 4 then
			player:dialogSeq(
				{
					t,
					"Keahlian Refining paling tinggi di antara semuanya. Kau hanya bisa mempelajari satu keahlian refining. Keahlian ini memungkinkanmu membuat barang berguna seperti senjata dan zirah.",
					"Kau harus sedikitnya level 50 untuk mempelajari keahlian refining."
				},
				1
			)
		elseif choice == 5 then
		end

		generalNPC.crafting_skills(player, npc)
	end
}
