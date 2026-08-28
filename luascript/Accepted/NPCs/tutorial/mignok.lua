MignokNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player:hasSpell("soothe") then
			player:dialogSeq({t, "Kau sudah punya mantra Soothe."}, {})
			return
		end

		if (player:hasItem("acorn", 5) == true and player:hasItem("rabbit_meat", 5) == true) then
			player:dialogSeq(
				{
					t,
					"Terima kasih, anak muda. Kau menunjukkan bahwa pikiran dan nuranimu waras. Akan kuajarkan mantra pertamamu."
				},
				1
			)

			player:removeItem("acorn", 5, 9)
			player:removeItem("rabbit_meat", 5, 9)

			if (not player:hasSpell("soothe")) then
				player:addSpell("soothe")
				player:giveXP(50)
			end

			player:dialogSeq(
				{
					t,
					"Pakailah mantra Soothe ini dengan bijaksana.",
					"Setelah kau mempelajari sebuah mantra, ia akan tampak di daftar mantramu ((Tekan + atau klik tab Spell untuk melihat daftarnya)).",
					"((Kau akan melihat tiap mantra punya huruf di sebelahnya. Huruf itu penting diingat saat merapal.))",
					"((Untuk merapal, tekan shift z atau Z lalu huruf mantranya. Kau juga bisa sekadar mengklik gandanya.))",
					"Seiring kau tumbuh, makin besar pula aether yang bisa kau kendalikan, sehingga kau bisa merapal mantra yang lebih kuat. Sebagian mantra menyedot banyak tenagamu, dan kau harus membiarkan aether di sekitarmu mengendap sebelum bisa merapalnya lagi.",
					"Hanya ini yang kuajarkan untuk sekarang. Semoga berhasil, anak muda, dan ingatlah memakai mantramu dengan bijak."
				},
				1
			)
			return
		end

		player:dialogSeq(
			{
				t,
				"Kekuatan sihir di dunia ini dikendalikan oleh yang disebut aether. Kalau kau bisa belajar mengendalikan dan memakainya, kau pun akan mampu menguasai kekuatan merapal mantra.",
				"Kalau itu pengetahuan yang ingin kau raih, tunjukkan padaku bahwa pikiranmu punya ketekunan dan kesabaran yang dibutuhkan untuk mengendalikan aether.",
				"Bunuh makhluk-makhluk di daerah ini untuk mengumpulkan lima acorn dan lima rabbit meat. Setelah itu kembalilah kepadaku dan akan kuajari kau."
			},
			1
		)
		if (player.registry["mignokexp"] == 0) then
			player:giveXP(15)
			player.registry["mignokexp"] = 1
		end
	end),

	move = function(npc, owner)
		--npc_ai.move(npc,owner)
	end
}
